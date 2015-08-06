/**
 * com.mckoi.appcore.UserApplicationsSchema  Apr 6, 2011
 *
 * Mckoi Database Software ( http://www.mckoi.com/ )
 * Copyright (C) 2000 - 2012  Diehl and Associates, Inc.
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 3 as published by
 * the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License version 3
 * along with this program.  If not, see ( http://www.gnu.org/licenses/ ) or
 * write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330,
 * Boston, MA  02111-1307, USA.
 *
 * Change Log:
 *
 *
 */

package com.mckoi.appcore;

import com.mckoi.data.DataFile;
import com.mckoi.data.DataFileUtils;
import com.mckoi.data.PropertySet;
import com.mckoi.odb.util.FileInfo;
import com.mckoi.odb.util.FileName;
import com.mckoi.odb.util.FileSystem;
import com.mckoi.util.StyledPrintWriter;
import java.io.*;
import java.util.*;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

/**
 * Various utility methods for managing the web applications context. Note
 * that this runs with the same privilege as the callee.
 *
 * @author Tobias Downer
 */

public class UserApplicationsSchema {

  /**
   * The webapps.xml configuration file location in the user's file
   * repository.
   */
  public static final String WEBAPPS_CONF  = "/config/mwp/webapps.xml";
  public static final String WEBAPPS_CONF2 = "/system/webapps.xml";

  /**
   * The webapps.pset object file that describes the web applications in a
   * binary format that can be quickly queried.
   */
  public static final String WEBAPPS_BIN  = "/system/webapps.pset";
  public static final String WEBAPPS_BIN2 = "/system/webapps.properties";

  /**
   * Returns the FileInfo for the webapp.xml file from the given account's
   * file system.
   */
  public static FileInfo getWebAppsXMLFileInfo(FileSystem repository) {
    // Look for both WEBAPPS_CONF and WEBAPPS_CONF2,
    FileInfo file_info = repository.getFileInfo(WEBAPPS_CONF);
    if (file_info == null) {
      return repository.getFileInfo(WEBAPPS_CONF2);
    }
    return file_info;
  }

  /**
   * Returns the FileInfo for the webapp.bin file from the given account's
   * file system.
   */
  public static FileInfo getWebAppsBinaryFileInfo(FileSystem repository) {
    // Look for both WEBAPPS_BIN and WEBAPPS_BIN2,
    FileInfo file_info = repository.getFileInfo(WEBAPPS_BIN);
    if (file_info == null) {
      return repository.getFileInfo(WEBAPPS_BIN2);
    }
    return file_info;
  }

  /**
   * Returns an ApplicationsDocument object for the 'webapps.xml'
   * configuration file that describes the applications currently operating
   * from the file repository. The returned document can be changed
   * and then saved using the 'writeApplicationsDocument' method. If the
   * webapps.xml file doesn't exist then a blank default document is
   * created.
   */
  public static ApplicationsDocument readApplicationsDocument(
                                                   FileSystem repository) {

    FileInfo file_info = getWebAppsXMLFileInfo(repository);
    InputStream din;

    try {

      // If the file doesn't exist, create a new blank one,
      if (file_info == null) {
        StringBuilder w = new StringBuilder();
        // PENDING: DTD?
        w.append("<?xml version=\"1.0\" encoding=\"UTF8\"?>\n\n");
        w.append("<!-- Configuration file for applications in the Mckoi Web Platform -->\n\n");
        w.append("<mckoi-apps>\n\n</mckoi-apps>\n");

        String str = w.toString();
        byte[] buf = str.getBytes("UTF-8");
        din = new ByteArrayInputStream(buf);
      }
      // File exists,
      else {
        din = new BufferedInputStream(
                         DataFileUtils.asInputStream(file_info.getDataFile()));
      }

      ApplicationsDocument doc = new ApplicationsDocument();

      // Load from the input stream,
      doc.loadFrom(din);

      return doc;

    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }

  }

  /**
   * Writes the ApplicationsDocument to the "/config/mwp/webapps.xml" file in
   * the given repository. This is called after the document is modified.
   * <p>
   * Note that the repository must be committed before any change takes
   * place permanently.
   */
  public static void writeApplicationsDocument(
                    FileSystem repository, ApplicationsDocument apps_doc) {

    try {

      FileInfo file_info = getWebAppsXMLFileInfo(repository);
      if (file_info == null) {
        repository.createFile(WEBAPPS_CONF, "application/xml",
                              System.currentTimeMillis());
        file_info = repository.getFileInfo(WEBAPPS_CONF);
      }

      DataFile dfile = file_info.getDataFile();
      dfile.setSize(0);
      dfile.position(0);

      Writer w = new BufferedWriter(new OutputStreamWriter(
                                DataFileUtils.asOutputStream(dfile), "UTF-8"));

      apps_doc.writeXML(w);
      w.flush();

      // Touch the file with the current timestamp.
      file_info.setLastModified(System.currentTimeMillis());

    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Updates the '/system/webapps.pset' file such that any web
   * application items under the given project_path are re-enumerated which
   * causes the web application infrastructure to reload it.
   * <p>
   * The given repository must be committed for the changes to take effect.
   * <p>
   * Returns true if the reload was successful, false if no web applications
   * were found under the project path, or there were errors (errors reported
   * to the StyledPrintWriter).
   */
  public static boolean reloadSystemWebAppsAt(
           FileSystem repository, String project_path, StyledPrintWriter out) {

    // The binary webapps configuration file,
    final FileInfo bin_fi = getWebAppsBinaryFileInfo(repository);
    if (bin_fi == null) {
      // Doesn't exist, so return false,
      return false;
    }

    // The existing file,
    DataFile bin_file = bin_fi.getDataFile();

    // The binary property set,
    PropertySet bin_set = new PropertySet(bin_file);

    // The current version number,
    long ver = bin_set.getLongProperty("v", 1);
    bin_set = new PropertySet(bin_file);

    // All the virtual hosts,
    SortedSet<String> vhs = bin_set.keySet().tailSet("vh.");
    ArrayList<String> vh_list = new ArrayList();
    for (String vh : vhs) {
      // Break the loop if we no longer are on vh's
      if (!vh.startsWith("vh.")) {
        break;
      }
      // The vh key,
      vh_list.add(vh);
      // The vh value (the location id).
      vh_list.add(bin_set.getProperty(vh));
    }

    // Key translations,
    HashMap<String, String> key_trans = new HashMap();

    // For each vh record,
    int sz = vh_list.size();
    for (int i = 0; i < sz; i += 2) {
      String id_str = vh_list.get(i + 1);

      // If the id has already been translated,
      String trans_id = key_trans.get(id_str);
      if (trans_id != null) {
        // Update the vh list,
        vh_list.set(i + 1, trans_id);
      }
      // Otherwise not translated yet, so check if it needs to be,
      else {
        String bkey = "p." + id_str;
        // Get the location repository path,
        String id_repository_path =
                               bin_set.getProperty(bkey + ".repository_path");
        if (id_repository_path == null) {
          throw new RuntimeException(
                                 "No property: " + bkey + ".repository_path");
        }
        // If it's a sub-directory of project_path then we need to rekey it,
        if (id_repository_path.startsWith(project_path)) {
          // Get the record properties,
          String name = bin_set.getProperty(bkey + ".name");
          String loc_path = bin_set.getProperty(bkey + ".path");
          String loc_repository =
                        bin_set.getProperty(bkey + ".repository");
          String loc_repository_path =
                        bin_set.getProperty(bkey + ".repository_path");
          String rights_str = bin_set.getProperty(bkey + ".rights");
          String loc_type = bin_set.getProperty(bkey + ".type");
          String vhosts_str = bin_set.getProperty(bkey + ".vhosts");

          // Remove,
          bin_set.setProperty(bkey + ".name", null);
          bin_set.setProperty(bkey + ".path", null);
          bin_set.setProperty(bkey + ".repository", null);
          bin_set.setProperty(bkey + ".repository_path", null);
          bin_set.setProperty(bkey + ".rights", null);
          bin_set.setProperty(bkey + ".type", null);
          bin_set.setProperty(bkey + ".vhosts", null);

          // Write it as a new key,
          String nid_str = Long.toString(ver);
          String nbkey = "p." + nid_str;

          // Store the properties,
          bin_set.setProperty(nbkey + ".name", name);
          bin_set.setProperty(nbkey + ".path", loc_path);
          // LEGACY
          if (loc_repository != null) {
            bin_set.setProperty(nbkey + ".repository", loc_repository);
          }
          bin_set.setProperty(nbkey + ".repository_path", loc_repository_path);
          bin_set.setProperty(nbkey + ".rights", rights_str);
          bin_set.setProperty(nbkey + ".type", loc_type);
          bin_set.setProperty(nbkey + ".vhosts", vhosts_str);

          // Update the vh list,
          vh_list.set(i + 1, nid_str);

          // Update the key translations,
          key_trans.put(bkey.substring(2), nbkey.substring(2));

          ++ver;
        }
      }
    }

    // Rewrite the vh list,
    for (int i = 0; i < sz; i += 2) {
      bin_set.setProperty(vh_list.get(i + 0), vh_list.get(i + 1));
    }

    // Update the id set,
    SortedSet<String> ids = bin_set.keySet().tailSet("id.");
    ArrayList<String> ids_list = new ArrayList();
    for (String id : ids) {
      if (!id.startsWith("id.")) {
        break;
      }
      ids_list.add(id);
    }

    // For each 'id.*' item,
    for (String id : ids_list) {
      String app_ids = bin_set.getProperty(id);
      List<String> app_ids_list = fromDelimString(app_ids);
      List<String> new_app_ids_list = new ArrayList(app_ids_list.size());
      // For each app id,
      for (String app_id : app_ids_list) {
        // Is this id translated?
        String nid = key_trans.get(app_id);
        if (nid == null) {
          new_app_ids_list.add(app_id);
        }
        else {
          new_app_ids_list.add(nid);
        }
      }
      bin_set.setProperty(id, toDelimString(new_app_ids_list));
    }

    // Update the version identifier,
    bin_set.setLongProperty("v", ver);

    // Set the timestamp of the file,
    bin_fi.setLastModified(System.currentTimeMillis());

    return true;

  }

  /**
   * Parses the "/config/mwp/webapps.xml" file in the repository and creates
   * the various structures for managing the web apps assigned to an account.
   * The given repository must be committed for the changes to take effect.
   * <p>
   * Returns true if the build was successful, false if there were errors
   * (errors reported to the StyledPrintWriter).
   */
  public static boolean buildSystemWebApps(
                               FileSystem repository, StyledPrintWriter out) {

    FileInfo file_info = getWebAppsXMLFileInfo(repository);

    if (file_info == null) {
      if (out != null) {
        out.println(
              "Web Application configuration file not found: " + WEBAPPS_CONF);
        out.flush();
      }
      return false;
    }

    try {
      InputStream din = new BufferedInputStream(
                      DataFileUtils.asInputStream(file_info.getDataFile()));

      // Load the document from the input stream,
      ApplicationsDocument doc = new ApplicationsDocument();
      doc.loadFrom(din);

      // Make the change to the binary webapps file,
      FileInfo bin_fi = getWebAppsBinaryFileInfo(repository);
      if (bin_fi == null) {
        // Doesn't exist, so create it
        repository.createFile(WEBAPPS_BIN,
                              "application/x-mckoi-propertyset",
                              System.currentTimeMillis());
        bin_fi = repository.getFileInfo(WEBAPPS_BIN);
      }

      // Clean the existing file,
      DataFile bin_file = bin_fi.getDataFile();

      // The binary property set,
      PropertySet bin_set = new PropertySet(bin_file);

      // Preserve the version number,
      long ver = bin_set.getLongProperty("v", 1);
      bin_file.setSize(0);
      bin_file.position(0);
      bin_set = new PropertySet(bin_file);

      // We insert a number of properties from the document that are used by
      // the application server to very quickly discover the data it needs.
      // The keys are;
      //
      //   v = version
      //
      //   id.(application name) = [application_id list]
      //     The unique id of the application name.
      //
      //   vh.(domain):(proto) = [application_id list]
      //     The list of application ids for the given virtual host.
      //
      //   p.(application_id).(property) = property_value
      //     A property of the application and location. Description follows.
      //
      //     p.(id).name
      //     p.(id).type
      //     p.(id).rights
      //     p.(id).vhosts
      //     p.(id).path
      //     p.(id).repository
      //     p.(id).repository_path

      List<Node> apps = doc.getAllApplications();
      for (Node app : apps) {
        // All the ids for this application record
        ArrayList<String> appid_ids = new ArrayList();

        // The application name,
        String name = doc.getAppName(app);

        List<Node> locations = doc.getAppLocations(app);
        for (Node loc : locations) {
          // Read the location properties,
          String loc_type = doc.getAttribute(loc, "type");
          String loc_path = doc.getAttribute(loc, "path");
          String loc_repository = doc.getAttribute(loc, "repository");
          String loc_repository_path = doc.getAttribute(loc, "repository_path");
          
          // Make sure the path is a directory,
          if (!loc_repository_path.endsWith("/")) {
            loc_repository_path = loc_repository_path + "/";
          }

          // Read the vhosts,
          List<String> vhosts = doc.getLocationVHosts(loc);
          // Read the rights,
          List<String> rights = doc.getLocationRights(loc);

          String id_str = Long.toString(ver);
          String bkey = "p." + id_str;

          // Store the properties,
          bin_set.setProperty(bkey + ".name", name);
          bin_set.setProperty(bkey + ".path", loc_path);
          // LEGACY,
          if (loc_repository != null) {
            bin_set.setProperty(bkey + ".repository", loc_repository);
          }
          bin_set.setProperty(bkey + ".repository_path", loc_repository_path);
          bin_set.setProperty(bkey + ".rights", toDelimString(rights));
          bin_set.setProperty(bkey + ".type", loc_type);
          bin_set.setProperty(bkey + ".vhosts", toDelimString(vhosts));

          // Update references for this app/location,
          for (String vhost : vhosts) {
            // The vhost string looks like this,
            //   [domain]:[proto]:[path]
            // For example;
            //   www.mckoi.com:*:/
            //   admin.mckoi.com:https:admin/
            //   www.mckoi.com:https:admin/
            StringBuilder b = new StringBuilder();
            b.append("vh.");
            b.append(vhost);
            b.append(':');
            b.append(loc_path);
            bin_set.setProperty(b.toString(), id_str);
          }

          appid_ids.add(id_str);

          ++ver;
        }

        // Encode the application ids into a set,
        String appid_ids_str = toDelimString(appid_ids);

        // Store the properties for the app,
        bin_set.setProperty("id." + name, appid_ids_str);

      }

      // Update the version identifier,
      bin_set.setLongProperty("v", ver);

      // Set the timestamp of the file,
      bin_fi.setLastModified(System.currentTimeMillis());

      return true;

    }
    catch (IOException e) {
      if (out != null) {
        out.print("IO Error: " + e.getMessage());
        out.printException(e);
        out.flush();
      }
      else {
        // PENDING: Put this on a logger,
        e.printStackTrace(System.err);
      }
      return false;
    }

  }

  /**
   * Convenience that returns a map of all the locations of the user
   * application with the given 'app_name' from the account's file system.
   * Returns null if an application with the given name is not found.
   * <p>
   * Use 'map.get("main")' for the location of the main build repository.
   */
  public static Map<String, FileName> getUserAppLocationMap(
                                        FileSystem file_sys, String app_name) {

    // Read the existing applications doc,
    ApplicationsDocument applications_doc =
                    UserApplicationsSchema.readApplicationsDocument(file_sys);

    // All the applications
    List<Node> apps = applications_doc.getAllApplications();
    Node found_app_node = null;
    for (Node app : apps) {
      if (applications_doc.getAppName(app).equals(app_name)) {
        found_app_node = app;
      }
    }

    // Not found so return null,
    if (found_app_node == null) {
      return null;
    }

    Map<String, FileName> map = new HashMap();

    // Fetch the build location path in the repository,
    List<Node> app_locations = applications_doc.getAppLocations(found_app_node);
    for (Node location: app_locations) {
      String location_type = applications_doc.getAttribute(location, "type");
      String repository =
                  applications_doc.getAttribute(location, "repository");
      String repository_path =
                  applications_doc.getAttribute(location, "repository_path");

      FileName loc_fname;
      // LEGACY,
      if (repository != null) {
        loc_fname = new FileName(repository, repository_path);
      }
      else {
        loc_fname = new FileName(repository_path);
      }
      map.put(location_type, loc_fname);

    }

    return map;

  }
  
  /**
   * Given a list of strings, returns a string that is delimited with
   * the '|' character. Reports an error if any strings in the list contain
   * the '|' character. Returns an empty string if no items in the list.
   */
  public static String toDelimString(List<String> list) {
    StringBuilder b = new StringBuilder();
    int sz = list.size();
    Iterator<String> it = list.iterator();
    int i = 0;
    while (it.hasNext()) {
      String item = it.next();
      if (item.indexOf('|') != -1) {
        throw new RuntimeException(
                       "Format error, list item contains deliminated string.");
      }
      b.append(item);
      if (i < sz - 1) {
        b.append("|");
      }
      ++i;
    }
    return b.toString();
  }

  /**
   * Given a deliminated string ('|' is the delim character) returns the list
   * as a collections object.
   */
  public static List<String> fromDelimString(String str) {
    ArrayList<String> string_list = new ArrayList();
    while (true) {
      int delim = str.indexOf("|");
      if (delim == -1) {
        if (str.length() > 0) {
          string_list.add(str);
        }
        return string_list;
      }
      string_list.add(str.substring(0, delim));
      str = str.substring(delim + 1);
    }
  }

  /**
   * Document builder.
   */
  private static DocumentBuilderFactory builder_factory =
                                          DocumentBuilderFactory.newInstance();

  public static String toXMLEntity(String text) {
    if (text == null) {
      return null;
    }
    int sz = text.length();
    StringBuilder output = new StringBuilder(sz + 128);
    for (int i = 0; i < sz; ++i) {
      char c = text.charAt(i);
      if (c == '"') {
        output.append("&quot;");
      }
      else if (c == '\'') {
        output.append("&apos;");
      }
      else if (c == '&') {
        output.append("&amp;");
      }
      else if (c == '<') {
        output.append("&lt;");
      }
      else if (c == '>') {
        output.append("&gt;");
      }
      else {
        output.append(c);
      }
    }
    return output.toString();
  }

  public static String sanityCheckString(String text) {
    if (text.indexOf('\"') != -1 ||
        text.indexOf('\'') != -1 ||
        text.indexOf('&') != -1 ||
        text.indexOf('<') != -1 ||
        text.indexOf('>') != -1) {
      throw new RuntimeException("Value failed sanity check: " + text);
    }
    return text;
  }


  /**
   * A user applications document expressed as a DOM object.
   */
  public static class ApplicationsDocument {

    /**
     * The DOM document.
     */
    private Document doc;

    /**
     * The web-apps node.
     */
    private Node web_apps;

    /**
     * Of the child nodes, returns the first found with the given tag name,
     * or null if a child with the tag is not found.
     */
    public Node findChildWithTag(Node el, String tag_name) {
      NodeList nlist = el.getChildNodes();
      int sz = nlist.getLength();
      for (int i = 0; i < sz; ++i) {
        Node c = nlist.item(i);
        if (c.getNodeName().equals(tag_name)) {
          return c;
        }
      }
      return null;
    }

    /**
     * Returns a list of all the child nodes from the given node with the
     * given tag.
     */
    public List<Node> findChildrenWithTag(Node el, String tag_name) {
      ArrayList<Node> found = new ArrayList();
      NodeList nlist = el.getChildNodes();
      int sz = nlist.getLength();
      for (int i = 0; i < sz; ++i) {
        Node c = nlist.item(i);
        if (c.getNodeName().equals(tag_name)) {
          found.add(c);
        }
      }
      return found;
    }

    /**
     * Loads the applications from the given input stream.
     */
    private void loadFrom(InputStream in) throws IOException {
      try {
        DocumentBuilder builder = builder_factory.newDocumentBuilder();
        doc = builder.parse(in);

        // Find the child with the 'web-apps' tag.
        web_apps = findChildWithTag(doc, "mckoi-apps");

      }
      catch (ParserConfigurationException e) {
        throw new RuntimeException(e);
      }
      catch (SAXException e) {
        throw new RuntimeException(e);
      }
    }

    /**
     * Returns a list of all the applications.
     */
    public List<Node> getAllApplications() {
      return findChildrenWithTag(web_apps, "app");
    }

    /**
     * Returns the name of the application given.
     */
    public String getAppName(Node app) {
      return app.getAttributes().getNamedItem("name").getNodeValue();
    }

    /**
     * Returns the description of the application give, or null if no
     * description set.
     */
    public String getAppDescription(Node app) {
      Node n = findChildWithTag(app, "description");
      return n.getTextContent();
    }

    /**
     * Returns the list of locations for the application.
     */
    public List<Node> getAppLocations(Node app) {
      return findChildrenWithTag(app, "location");
    }

    /**
     * Returns the value of an attribute, or null if the attribute not found.
     */
    public String getAttribute(Node el, String attribute) {
      Node n = el.getAttributes().getNamedItem(attribute);
      if (n != null) {
        return n.getNodeValue();
      }
      return null;
    }

    /**
     * Returns the list of vhosts for the location.
     */
    public List<String> getLocationVHosts(Node location) {
      ArrayList<String> vhosts_result = new ArrayList();
      Node vhost = findChildWithTag(location, "vhosts");
      if (vhost != null) {
        List<Node> vhosts = findChildrenWithTag(vhost, "vhost");
        for (Node n : vhosts) {
          Node protocol = n.getAttributes().getNamedItem("protocol");
          Node domain = n.getAttributes().getNamedItem("domain");
          String vhost_str = domain.getNodeValue();
          if (protocol != null) {
            vhost_str += (":" + protocol.getNodeValue());
          }
          else {
            vhost_str += (":*");
          }

          vhosts_result.add(vhost_str);
        }
      }
      return vhosts_result;
    }

    /**
     * Returns the list of rights defined for the location.
     */
    public List<String> getLocationRights(Node location) {
      ArrayList<String> rights_result = new ArrayList();
      Node rights = findChildWithTag(location, "rights");
      if (rights != null) {
        List<Node> rights_l = findChildrenWithTag(rights, "right");
        for (Node n : rights_l) {
          rights_result.add(
                      n.getAttributes().getNamedItem("value").getNodeValue());
        }
      }
      return rights_result;
    }


    /**
     * Given an XML string describing an application, adds the application
     * entry to the document.
     */
    public void addApplicationXML(String xml_string) throws IOException {
      Node new_app = parse(xml_string);
      NodeList new_app_nodes = new_app.getChildNodes();
      int sz = new_app_nodes.getLength();
      for (int i = 0; i < sz; ++i) {
        Node n = new_app_nodes.item(i);
        web_apps.appendChild(doc.importNode(n, true));
      }
    }

    /**
     * Adds a default XML application entry with a single location entry.
     * Does nothing if an application with the same name is already found in
     * the document. Returns true if the document changed, false otherwise.
     */
    public boolean addDefaultApplication(
            String name, String description,
            String path, String repository, String repository_path,
            Collection<String> vhosts, String grant) {

      List<Node> apps = getAllApplications();
      for (Node app : apps) {
        if (getAppName(app).equals(name)) {
          return false;
        }
      }

      name = toXMLEntity(name);
      description = toXMLEntity(description);
      path = toXMLEntity(path);
      repository = toXMLEntity(repository);
      repository_path = toXMLEntity(repository_path);
      grant = toXMLEntity(grant);

      try {

        // This generates the XML with the appropriate properties
        StringBuilder b = new StringBuilder();
        b.append("  <app name='").append(name).append("'>\n");
        if (description != null) {
          b.append("    <description>").append(description).append("</description>\n");
        }
        b.append("    <location type='main'\n");
        b.append("              path='").append(path).append("'\n");
        // LEGACY,
        if (repository != null) {
          b.append("              repository='").append(repository).append("'\n");
        }
        b.append("              repository_path='").append(repository_path).append("'\n");
        b.append("    >\n");
        b.append("\n");
        b.append("      <vhosts>\n");

        for (String vhost : vhosts) {
          String domain, protocol;
          int delim = vhost.lastIndexOf(":");
          if (delim != -1) {
            domain = toXMLEntity(vhost.substring(0, delim));
            protocol = toXMLEntity(vhost.substring(delim + 1));
          }
          else {
            domain = toXMLEntity(vhost);
            protocol = "*";
          }
          b.append("        <vhost domain = '").append(domain).
                     append("' protocol = '").append(protocol).append("' />\n");
        }

        b.append("      </vhosts>\n");
        b.append("\n");
        b.append("      <rights>\n");
        b.append("        <right value = '").append(grant).append("' />\n");
        b.append("      </rights>\n");
        b.append("\n");
        b.append("    </location>\n");
        b.append("\n");
        b.append("  </app>\n\n");

        addApplicationXML(b.toString());

        return true;

      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }



    /**
     * Turns the given text into a DOM object.
     */
    public Node parse(String text) throws IOException {
      try {
        DocumentBuilder builder = builder_factory.newDocumentBuilder();
        text = "<fragment>" + text + "</fragment>";
        Document d = builder.parse(new InputSource(new StringReader(text)));
        return findChildWithTag(d, "fragment");
      }
      catch (ParserConfigurationException e) {
        throw new RuntimeException(e);
      }
      catch (SAXException e) {
        throw new RuntimeException(e);
      }
    }


    /**
     * Prints the line indent.
     */
    private void printIndent(Writer out, int indent) throws IOException {
      for (int n = 0; n < indent; ++n) {
        out.append(" ");
      }
    }

    /**
     * Writes the attributes of a node out as an XML.
     */
    private void writeAttributesAsXML(Writer out,
                                Node node, int indent) throws IOException {

      if (node.hasAttributes()) {
        out.append(" ");
        NamedNodeMap attributes = node.getAttributes();
        int sz = attributes.getLength();
        for (int i = 0; i < sz; ++i) {
          out.append(sanityCheckString(attributes.item(i).getNodeName()) +
                     "=\"" +
                       toXMLEntity(attributes.item(i).getNodeValue()) +
                     "\"");
          if (i < sz - 1) {
            out.append(" ");
          }
        }
      }
    }



    private void writeLocationAttributesAsXML(Writer out,
                                Node node, int indent) throws IOException {

      out.append(" ");
      NamedNodeMap attributes = node.getAttributes();
      String type = attributes.getNamedItem("type").getNodeValue();
      String path = attributes.getNamedItem("path").getNodeValue();
      Node repository_node = attributes.getNamedItem("repository");
      String fr_path =
             (repository_node == null) ? null : repository_node.getNodeValue();
      String context_path =
             attributes.getNamedItem("repository_path").getNodeValue();

      out.append("type=\"");
      out.append(toXMLEntity(type));
      out.append("\"\n");
      printIndent(out, 14);
      out.append("path=\"");
      out.append(toXMLEntity(path));
      out.append("\"\n");
      if (fr_path != null) {
        printIndent(out, 14);
        out.append("repository=\"");
        out.append(toXMLEntity(fr_path));
        out.append("\"\n");
      }
      printIndent(out, 14);
      out.append("repository_path=\"");
      out.append(toXMLEntity(context_path));
      out.append("\"\n");
      printIndent(out, 4);

    }


    private void writeTagAttributes(Writer out, String tag,
                                Node node, int indent) throws IOException {
      // We handle the attributes of the location entity specially,
      if (tag.equals("location")) {
        writeLocationAttributesAsXML(out, node, indent);
      }
      else {
        writeAttributesAsXML(out, node, indent);
      }
    }

    /**
     * Recursive pretty print to XML.
     */
    private void printNodeAsXML(Writer out,
                                Node node, int indent) throws IOException {

      NodeList children = node.getChildNodes();
      int sz = children.getLength();
      for (int i = 0; i < sz; ++i) {
        Node n = children.item(i);
        short type = n.getNodeType();
        if (type == Node.COMMENT_NODE) {
          out.append("<!-- ");
          out.append(n.getNodeValue());
          out.append("-->");
          if (indent == 0) {
            out.append("\n\n");
          }
        }
        else if (type == Node.TEXT_NODE) {
          out.append(toXMLEntity(n.getNodeValue()));
        }
        else {
          // If it has children,
          String tag = sanityCheckString(n.getNodeName());
          if (n.hasChildNodes()) {
            out.append("<");
            out.append(tag);
            writeTagAttributes(out, tag, n, indent);
            out.append(">");
            printNodeAsXML(out, n, indent + 2);
            out.append("</");
            out.append(tag);
            out.append(">");
          }
          // No child nodes,
          else {
            out.append("<");
            out.append(tag);
            writeTagAttributes(out, tag, n, indent);
            out.append(" />");
          }
        }
      }

    }

    /**
     * Outputs the DOM as a string.
     */
    public void writeXML(Writer out) throws IOException {

      out.append("<?xml version=\"1.0\" encoding=\"UTF8\"?>\n\n");

      printNodeAsXML(out, doc, 0);

    }

//    /**
//     * Testing.
//     */
//    private void test() throws IOException {
//      List<Node> apps_list = getAllApplications();
//      for (Node app : apps_list) {
//        System.out.println(getAppName(app));
//        System.out.println(getAppDescription(app));
//        List<Node> app_locations = getAppLocations(app);
//        for (Node location : app_locations) {
//          System.out.println("TYPE: " + getAttribute(location, "type"));
//          System.out.println("PATH: " + getAttribute(location, "path"));
//          System.out.println("FPATH: " + getAttribute(location, "file_repository_path"));
//          System.out.println("CPATH: " + getAttribute(location, "context_path"));
//          List<String> vhosts = getLocationVHosts(location);
//          System.out.println("VHOSTS: " + vhosts);
//          List<String> rights = getLocationRights(location);
//          System.out.println("RIGHTS: " + rights);
//        }
//      }
//
//      // Create a new app,
//      StringBuilder b = new StringBuilder();
//      b.append("  <app name='test'>\n");
//      b.append("    <description />\n");
//      b.append("    <location type='main'\n");
//      b.append("              path='/rawr'\n");
//      b.append("              repository='$user'\n");
//      b.append("              repository_path='/apps/rawr/'\n");
//      b.append("    />\n");
//      b.append("\n");
//      b.append("    <vhosts>\n");
//      b.append("      <vhost domain = 'apptest.localhost' protocol = 'http' />\n");
//      b.append("    </vhosts>\n");
//      b.append("\n");
//      b.append("    <rights>\n");
//      b.append("      <right value = 'grant read path:/data/rawr/**' />\n");
//      b.append("      <right value = 'grant write path:/data/rawr/**' />\n");
//      b.append("    </rights>\n");
//      b.append("\n");
//      b.append("  </app>\n\n");
//
//      addApplicationXML(b.toString());
//
//
//
//
//
//      System.out.println("XML REPRESENTATION");
//
//      StringWriter str = new StringWriter();
//      writeXML(str);
//      str.flush();
//
//      System.out.println(str);
//
//    }

  }

}
