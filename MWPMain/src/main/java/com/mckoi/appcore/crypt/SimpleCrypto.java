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
 
package com.mckoi.appcore.crypt;

import javax.crypto.Cipher;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * A simple encryption mechanism that can encrypt a ByteBuffer that can
 * only be decrypted by the same SimpleCrypto instance or an instance that
 * shares the same secret key. Useful when sending state to an untrusted
 * client that you want to forget and later rebuild if needed.
 *
 * This will not generate errors during decrypt if there are transmission
 * errors or malicious changes to the encrypted data. Error checking must be
 * performed after the message is decrypted.
 *
 * Instances of this class are NOT thread-safe.
 *
 * @author Tobias Downer
 */

public class SimpleCrypto implements Crypto {

  // The cipher used

  private final SecureRandom secure_random;
  private final Cipher cipher;
  private final SecretKeySpec secret_key;

  private final byte[] iv_bytes = new byte[16];
  private final byte[] buffer = new byte[1024];
  private final byte[] buffer2 = new byte[4096];

  /**
   * Constructs a SimpleCrypto with the given secret key. The secret key
   * should be 16 bytes in length.
   */
  public SimpleCrypto(byte[] secret_key_bytes) {
    try {
      secure_random = new SecureRandom();
      if (secret_key_bytes == null) {
        secret_key_bytes = new byte[16];
        secure_random.nextBytes(secret_key_bytes);
      }
      cipher = Cipher.getInstance("AES/CFB/NoPadding");
      secret_key = new SecretKeySpec(secret_key_bytes, "AES");
    }
    catch (NoSuchAlgorithmException | NoSuchPaddingException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Constructs a SimpleCrypto with a random secret key that is only
   * accessible to this instance.
   */
  public SimpleCrypto() {
    this(null);
  }

  /**
   * Encrypts 'src' buffer and writes encrypted bytes to 'dst'. The
   * 'dst' buffer must have a limit 16 bytes or more greater than the
   * 'src' limit. After call, the 'dst' limit will be set to the end of
   * the data encrypted.
   */
  public void encrypt(ByteBuffer src, ByteBuffer dst) throws IOException {

    // Create a random IV,
    secure_random.nextBytes(iv_bytes);
    // Write it to the output stream,
    dst.put(iv_bytes, 0, iv_bytes.length);

    try {

      IvParameterSpec iv_spec = new IvParameterSpec(iv_bytes);

      cipher.init(Cipher.ENCRYPT_MODE, secret_key, iv_spec);

      while (src.remaining() > 0) {
        cipher.update(src, dst);
      }
      cipher.doFinal(src, dst);

      src.rewind();
      dst.flip();

    }
    catch (GeneralSecurityException ex) {
      throw new RuntimeException(ex);
    }

  }

  /**
   * Decrypts 'src' buffer and writes decrypted bytes to 'dst'. The 'dst'
   * buffer must have a limit 16 bytes or less smaller than 'src'. After
   * call, the 'dst' limit will be set to the end of the data decrypted.
   */
  public void decrypt(ByteBuffer src, ByteBuffer dst) throws IOException {

    src.get(iv_bytes, 0, iv_bytes.length);

    try {

      IvParameterSpec iv_spec = new IvParameterSpec(iv_bytes);

      cipher.init(Cipher.DECRYPT_MODE, secret_key, iv_spec);

      while (src.remaining() > 0) {
        cipher.update(src, dst);
      }
      cipher.doFinal(src, dst);

      src.rewind();
      dst.flip();

    }
    catch (GeneralSecurityException ex) {
      throw new RuntimeException(ex);
    }

  }





  public void encrypt(InputStream ins, OutputStream outs) throws IOException {

    // Create a random IV,
    secure_random.nextBytes(iv_bytes);
    // Write it to the output stream,
    outs.write(iv_bytes, 0, iv_bytes.length);

    try {

      IvParameterSpec iv_spec = new IvParameterSpec(iv_bytes);

      cipher.init(Cipher.ENCRYPT_MODE, secret_key, iv_spec);

      while (true) {
        int read = ins.read(buffer, 0, buffer.length);
        if (read == -1) {
          int b2len = cipher.doFinal(buffer2, 0);
          outs.write(buffer2, 0, b2len);
          break;
        }
        int b2len = cipher.update(buffer, 0, read, buffer2, 0);
        outs.write(buffer2, 0, b2len);
      }

    }
    catch (GeneralSecurityException ex) {
      throw new RuntimeException(ex);
    }

  }

  public void decrypt(InputStream ins, OutputStream outs) throws IOException {

    // Read the iv,
    int i = 0;
    while (i < 16) {
      int read = ins.read(iv_bytes, i, 16 - i);
      if (read == -1) {
        throw new IOException("Unexpected end of stream reached");
      }
      i += read;
    }

    try {

      IvParameterSpec iv_spec = new IvParameterSpec(iv_bytes);

      cipher.init(Cipher.DECRYPT_MODE, secret_key, iv_spec);

      while (true) {
        int read = ins.read(buffer, 0, buffer.length);
        if (read == -1) {
          int b2len = cipher.doFinal(buffer2, 0);
          outs.write(buffer2, 0, b2len);
          break;
        }
        int b2len = cipher.update(buffer, 0, read, buffer2, 0);
        outs.write(buffer2, 0, b2len);
      }

    }
    catch (GeneralSecurityException ex) {
      throw new RuntimeException(ex);
    }

  }


  public String bufferToBase64(ByteBuffer bb) {
    ByteBuffer cb = bb.duplicate();
    // This is an awkward array copy.
    // It's a shame there isn't an 'encodeToString(ByteBuffer)' method in the
    // encoder.
    int to_copy = cb.remaining();
    byte[] buf = new byte[to_copy];
    cb.get(buf, 0, to_copy);
    return Base64.getEncoder().withoutPadding().encodeToString(buf);
  }

  public ByteBuffer base64ToBuffer(String base64str) {
    return ByteBuffer.wrap(Base64.getDecoder().decode(base64str));
  }


}
