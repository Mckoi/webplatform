/*
 * Mckoi Software ( http://www.mckoi.com/ )
 * Copyright (C) 2000 - 2016  Tobias Downer.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
 
package com.mckoi.appcore;

import com.mckoi.appcore.crypt.Crypto;
import com.mckoi.data.NodeReference;
import com.mckoi.network.*;
import com.mckoi.odb.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.List;

/**
 * A cache for MckoiDDB session data. This is a cache that may be shared
 * between several services. It is a thread-safe way to create transactions
 * that helps minimize the load on the root servers by caching root data
 * states. The down side to using this cache is that data is worst-case, 4
 * seconds out of date.
 * <p>
 * SECURITY: Care must be taken to ensure this object does not end up in
 *   user-code. User access to this object will expose access to the MckoiDDB
 *   infrastructure API.
 *
 * @author Tobias Downer
 */

public class DBSessionCache {

  /**
   * The client access to the MckoiDDB network.
   */
  private final MckoiDDBClient client;

  /**
   * The network config resource for admin operations.
   */
  private final NetworkConfigResource network_config_resource;

  /**
   * Map from path name to SessionStates.
   */
  private final HashMap<String, ODBSessionState> session_map;

  /**
   * The maximum time (in milliseconds) of how out of date a transaction may
   * be. Defaults to 4 seconds.
   */
  private final int max_time_out_of_date;

  /**
   * Constructor.
   */
  public DBSessionCache(MckoiDDBClient client,
                        NetworkConfigResource network_config_resource,
                        int max_time_out_of_date) {

    if (client == null) throw new NullPointerException();
    if (network_config_resource == null) throw new NullPointerException();

    this.client = client;
    this.network_config_resource = network_config_resource;
    this.max_time_out_of_date = max_time_out_of_date;

    session_map = new HashMap<>();
  }

  /**
   * Returns the MckoiDDBClient that backs this session cache.
   */
  protected final MckoiDDBClient getMckoiDDBClient() {
    return client;
  }

  /**
   * Returns the NetworkConfigResource that backs this session cache.
   */
  protected final NetworkConfigResource getNetworkConfigResource() {
    return network_config_resource;
  }

  /**
   * Returns an internal ODBSessionState object. Caches sessions for reuse.
   */
  private ODBSessionState getODBSessionState(final String path) {
    ODBSessionState state;
    synchronized (session_map) {
      state = session_map.get(path);
      if (state == null) {
        state = new ODBSessionState();
        state.last_update = -1;
        state.session = new ODBSession(client, path);
        session_map.put(path, state);
      }
    }
    return state;
  }

  /**
   * Returns a DBPathSnapshot object representing the latest snapshot of
   * the database path. This can be built into a transaction by using the
   * 'createODBTransaction' methods.
   * <p>
   * The snapshot is lazily created so it may not represent the very latest
   * version available (the snapshot will be at most 'max_time_out_of_date'
   * milliseconds out of date).
   */
  public DBPathSnapshot getLatestDBPathSnapshot(final String path) {

    final long time_now = System.currentTimeMillis();

    // Get the session state,
    ODBSessionState state = getODBSessionState(path);

    // Synchronize over the state object
    synchronized (state) {
      ODBSession session = state.session;
      // Is it time to fetch a new snapshot?
      if (state.last_update < time_now - max_time_out_of_date) {
        // Yes, so fetch the current snapshot
        state.root_address = session.getCurrentSnapshot();
        state.last_update = time_now;
      }

      // Return the root address object,
      return new DBPathSnapshot(session, state.root_address);
    }

  }

  /**
   * Materializes an ODBTransaction given a DBPathSnapshot.
   */
  public ODBTransaction createODBTransaction(DBPathSnapshot snapshot) {

    ODBTransaction t = snapshot.getSession().createTransaction(
            snapshot.getODBRootAddress());

    // Wrap the transaction,
    return new WrappedODBTransaction(t);

  }

  /**
   * Materializes a read-only ODBTransaction given a DBPathSnapshot.
   */
  public ODBTransaction createReadOnlyODBTransaction(DBPathSnapshot snapshot) {

    // Create the ODBTransaction object on the current snapshot,
    return snapshot.getSession().createReadOnlyTransaction(
            snapshot.getODBRootAddress());

  }

  /**
   * Returns a ODBTransaction object for the latest version of the given path.
   * The transaction is lazily created (the snapshot will be at most
   * 'max_time_out_of_date' milliseconds out of date).
   */
  public ODBTransaction getODBTransaction(final String path) {
    return createODBTransaction(getLatestDBPathSnapshot(path));
  }

  /**
   * Returns a read-only ODBTransaction object representing the latest version
   * of the given path. The transaction is lazily created (the snapshot will
   * be at most 'max_time_out_of_date' milliseconds out of date).
   */
  public ODBTransaction getReadOnlyODBTransaction(final String path) {
    return createReadOnlyODBTransaction(getLatestDBPathSnapshot(path));
  }

  /**
   * Commits a transaction, and if successful updates the internal state so
   * further requests for transaction for the path return a view of the
   * transaction that includes the changes.
   */
  public ODBRootAddress commitODBTransaction(ODBTransaction transaction)
          throws CommitFaultException {

    // Get the path of the transaction,
    String path = transaction.getSessionPathName();

    // Commit the transaction,
    ODBRootAddress new_root =
            ((WrappedODBTransaction) transaction).internalCommit();
    // Note that the above commit may throw a commit fault exception which
    // means we don't proceed.
    // If the commit is successful however, the state objects are updated.
    final long time_now = System.currentTimeMillis();

    // If the commit is successful,
    // Get the session state,
    ODBSessionState state = getODBSessionState(path);

    // Update the state,
    synchronized (state) {
      // Update the state
      state.root_address = new_root;
      state.last_update = time_now;
    }

    return new_root;

  }

  /**
   * Encodes an encrypted ByteBuffer from a DBPathSnapshot object. The
   * generated ByteBuffer can later be decoded to regenerate the
   * snapshot.
   *
   * Only clients with the secret key will be able to decode the
   * generated ciphertext.
   *
   * Note that this can't be used to preserve the state of an ongoing
   * transaction.
   */
  public ByteBuffer encodeDBPathSnapshot(Crypto crypter, DBPathSnapshot snapshot) {

    // The minimal data we need to serialize a DBPathSnapshot is the path name
    // and 128-bit node reference.
    String path = snapshot.getSession().getPathName();
    DataAddress data_addr = snapshot.getODBRootAddress().secureGetDataAddress();
    NodeReference node_ref = data_addr.getValue();
    long high = node_ref.getHighLong();
    long low = node_ref.getLowLong();

    // In memory or special node references can not be encoded.
    if (node_ref.isInMemory() || node_ref.isSpecial()) {
      throw new RuntimeException("Only able to encode a persistent node reference");
    }

    // Have all the information we need to encode,
    try {

      byte[] utf8_bytes = path.getBytes("UTF-8");
      int len = 2 + 1 + 1 + 4 + utf8_bytes.length + 8 + 8 + 2;
      ByteBuffer in = ByteBuffer.allocate(len);

      // Serialize the relevant data (minus the CRC shorts),
      in.position(2);
      in.put((byte) 1);  // type
      in.put((byte) 0);  // flags
      in.putInt(utf8_bytes.length);
      in.put(utf8_bytes);
      in.putLong(high);
      in.putLong(low);

      // Perform a CRC,
      in.position(2);
      int crc = 0;
      for (int i = 0; i < len - 4; ++i) {
        crc += Math.abs((int) in.get());
      }

      // Write the CRC short to the start and end of the message,
      in.position(0);
      in.putShort((short) crc);
      in.position(len - 2);
      in.putShort((short) crc);

      // Encrypt the message,
      ByteBuffer out = ByteBuffer.allocate(len + 16);
      crypter.encrypt(in, out);

      return out;

    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }

  }

  /**
   * Decodes a ByteBuffer to a DBPathSnapshot that has previously been
   * encoded with 'encodeDBPathSnapshot'.
   */
  public DBPathSnapshot decodeDBPathSnapshot(Crypto crypter, ByteBuffer bb) {

    try {
      // Decrypt the buffer,
      ByteBuffer in = ByteBuffer.allocate(bb.limit() - 16);
      crypter.decrypt(bb, in);

      // Check the CRC values in case of transmission errors / tampering
      int len = in.limit();
      in.position(0);
      short crc1 = in.getShort();
      in.position(len - 2);
      short crc2 = in.getShort();

      in.position(2);
      int tcrc = 0;
      for (int i = 0; i < len - 4; ++i) {
        int v = Math.abs((int) in.get());
        tcrc += v;
      }

      // Oops CRC failed. Note that we shouldn't differentiate exceptions
      // for CRC mismatch vs bookend mismatch. This could open possible
      // encryption weaknesses.
      if ( crc1 != crc2 || (short) tcrc != crc1 ) {
        throw new RuntimeException("CRC failed");
      }

      // Passed crc, so extract the info,
      in.position(2);
      byte type = in.get();
      if (type != 1) {
        throw new RuntimeException("Invalid message type");
      }
      byte flags = in.get();
      // Deserialize the path name,
      int strlen = in.getInt();
      byte[] utf8_bytes = new byte[strlen];
      in.get(utf8_bytes);
      // The 128-bit root node reference,
      long high = in.getLong();
      long low = in.getLong();

      // Create the path snapshot,
      String path = new String(utf8_bytes, "UTF-8");
      // Create a DataAddress for the root NodeReference,
      DataAddress data_address =
              DataAddress.createDataAddress(new NodeReference(high, low));

      // Create the DBPathSnapshot object,
      ODBSessionState state = getODBSessionState(path);
      ODBSession session = state.session;
      ODBRootAddress root_address =
              ODBRootAddress.createODBRootAddress(session, data_address);

      // Return the DBPathSnapshot,
      return new DBPathSnapshot(session, root_address);

    }
    catch (IOException ex) {
      throw new RuntimeException(ex);
    }

  }



  // ----- Inner classes -----

  /**
   * Wrapped ODBTransaction that overrides 'commit' to go through the
   * sessions cache.
   */
  private class WrappedODBTransaction implements ODBTransaction {

    private ODBTransaction backed;

    WrappedODBTransaction(ODBTransaction backed) {
      this.backed = backed;
    }

    /**
     * The internal commit method that delegates to the wrapped transaction.
     */
    ODBRootAddress internalCommit() throws CommitFaultException {
      return backed.commit();
    }

    @Override
    public ODBRootAddress commit() throws CommitFaultException {
      // Override commit to use session commit,
      return commitODBTransaction(this);
    }

    @Override
    public boolean removeNamedItem(String name) {
      return backed.removeNamedItem(name);
    }

    @Override
    public String getSessionPathName() {
      return backed.getSessionPathName();
    }

    @Override
    public ODBObject getObject(ODBClass type, Reference ref) {
      return backed.getObject(type, ref);
    }

    @Override
    public List<String> getNamedItemsList() {
      return backed.getNamedItemsList();
    }

    @Override
    public ODBObject getNamedItem(String name) {
      return backed.getNamedItem(name);
    }

    @Override
    public List<String> getClassNamesList() {
      return backed.getClassNamesList();
    }

    @Override
    public ODBClassCreator getClassCreator() {
      return backed.getClassCreator();
    }

    @Override
    public ODBClass getClass(Reference ref) {
      return backed.getClass(ref);
    }

    @Override
    public ODBClass findClass(String class_name) {
      return backed.findClass(class_name);
    }

    @Override
    public void doGarbageCollection() {
      backed.doGarbageCollection();
    }

    @Override
    public ODBObject constructObject(ODBClass clazz, Object... args) {
      return backed.constructObject(clazz, args);
    }

    @Override
    public void addNamedItem(String name, ODBObject item) {
      backed.addNamedItem(name, item);
    }

  }

  private static class ODBSessionState {

    long last_update;
    ODBSession session;
    ODBRootAddress root_address;

  }


}
