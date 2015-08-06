/*
 * Copyright 2015 Tobias Downer.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.mckoi.mwpclient.http;

import com.mckoi.mwpclient.http.SelfSignedCertificateException.FailType;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.Charset;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.*;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSession;
import org.apache.http.HttpEntity;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.ssl.DefaultHostnameVerifier;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.ContentType;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.ssl.SSLContextBuilder;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

/**
 * An object that maintains a client connection with an application running
 * on a HTTP server that returns JSON objects as a result.
 * <p>
 * This client handles secure https communication on servers with self-signed 
 * certificates.
 *
 * @author Tobias Downer
 */
public class WebClient {

  /**
   * The HttpClient.
   */
  private CloseableHttpClient httpclient;

  /**
   * The local certificate info host->cert_info_data map.
   */
  private final Map<String, LocalCertificateData> cert_map = new HashMap();

  /**
   * Creates the HttpClient we use to talk with server.
   */
  private CloseableHttpClient createHttpClient()
                                              throws GeneralSecurityException {

    HttpClientBuilder builder = HttpClientBuilder.create();

    // Our SSL context should accept self-signed certificates.
    SSLContextBuilder ssl_builder = new SSLContextBuilder();
    ssl_builder.loadTrustMaterial(null, new TrustSelfSignedStrategy());

    // Our verifier works out if a self-signed certificate should be allowed
    // or not. It bases its decision on if the certificate data is previously
    // known.
    HostnameVerifier host_verifier = new CustomHostnameVerifier();

    SSLConnectionSocketFactory sslsf =
            new SSLConnectionSocketFactory(ssl_builder.build(), host_verifier);
    CloseableHttpClient client = builder.setSSLSocketFactory(sslsf).build();
    
    return client;

  }

  /**
   * Converts a hash byte array into a fixed sized string where each byte is
   * deliminated with the 'byte_delim' string.
   * 
   * @param buf
   * @param byte_delim
   * @return 
   */
  public static String hexEncode(byte[] buf, String byte_delim) {
    StringBuilder b = new StringBuilder();
    for (int i = 0; i < buf.length; ++i) {
      if (i > 0) {
        b.append(byte_delim);
      }
      int n = ((int) buf[i]) & 0x0FF;
      String hex = Integer.toHexString(n);
      if (hex.length() == 1) {
        b.append('0');
      }
      b.append(hex);
    }
    return b.toString();
    
  }

  /**
   * Converts a hash byte array into a fixed sized string.
   * 
   * @param buf
   * @return 
   */
  public static String hexEncode(byte[] buf) {
    return hexEncode(buf, "");
  }

  /**
   * Decodes a hex string with a deliminating string between hex values.
   * 
   * @param data
   * @param byte_delim
   * @return 
   */
  public static byte[] hexDecode(String data, String byte_delim) {
    int count = 0;
    if (data.length() > 0) {
      count = data.length() / (2 + byte_delim.length());
      if (byte_delim.length() > 0) {
        count += 1;
      }
    }
    byte[] out = new byte[count];
    try {
      char[] val_buf = new char[2];
      char[] delim_buf = new char[byte_delim.length()];
      StringReader stin = new StringReader(data);
      for (int i = 0; i < count; ++i) {
        if (i > 0) {
          if (delim_buf.length > 0) {
            stin.read(delim_buf, 0, delim_buf.length);
            if (!byte_delim.equals(new String(delim_buf))) {
              throw new IOException("Format error");
            }
          }
        }
        stin.read(val_buf, 0, 2);
        int hex_val = Integer.parseInt(new String(val_buf), 16);
        out[i] = (byte) hex_val;
      }
      return out;
    }
    catch (IOException ex) {
      // Shouldn't be possible.
      throw new RuntimeException(ex);
    }
  }
  
  /**
   * Decodes a hex string.
   * 
   * @param data
   * @return 
   */
  public static byte[] hexDecode(String data) {
    return hexDecode(data, "");
  }
  

  /**
   * Returns a sha1 thumbprint of the given certificate's encoded data. This
   * is used to display to the user so they can confirm the certificate.
   * 
   * @param cert
   * @return 
   */
  public static String createSHA1Thumbprint(Certificate cert) {
    try {

      // The certificate data (eg DER format)
      byte[] cert_data = cert.getEncoded();

      // Use sha1 to generate the thumbprint of the certificate's encoded data,
      MessageDigest digest;
      try {
        digest = MessageDigest.getInstance("SHA1");
      }
      catch (NoSuchAlgorithmException e) {
        throw new RuntimeException(e);
      }

      digest.update(cert_data, 0, cert_data.length);
      byte[] thumbprint = digest.digest();
      
      // Return it has a string.
      return hexEncode(thumbprint, " ");

    }
    catch (CertificateEncodingException ex) {
      // Shouldn't happen,
      throw new RuntimeException(ex);
    }

  }

  /**
   * Initializes the WebClient.
   * 
   * @throws GeneralSecurityException 
   */
  public void init() throws GeneralSecurityException, IOException {
    if (this.httpclient != null) {
      throw new IllegalStateException("Already initialized");
    }
    // Create the HttpClient
    this.httpclient = createHttpClient();
    // Load any saved certificates,
    loadLocalCertificates();
  }

  /**
   * The file in which certifications are stored locally.
   * 
   * @return
   * @throws IOException 
   */
  private static File getLocalCertsFile() throws IOException {
    String user_home = System.getProperty("user.home");
    File user_home_dir = new File(user_home, ".mwpclient");
    if (!user_home_dir.exists()) {
      user_home_dir.mkdir();
    }
    else if (!user_home_dir.isDirectory()) {
      throw new IOException(user_home_dir.toString());
    }
    
    return new File(user_home_dir, "certs.txt").getCanonicalFile();
  }

  /**
   * Loads all certificates we know about saved in the user home directory.
   * 
   * @throws IOException 
   */
  private void loadLocalCertificates() throws IOException {
    File user_home_file = getLocalCertsFile();
    cert_map.clear();
    if (user_home_file.exists()) {
      try (BufferedReader bin = new BufferedReader(
                new InputStreamReader(
                        new FileInputStream(user_home_file), "UTF-8"))) {
        String encoding = bin.readLine();
        if (!encoding.equals("1")) {
          throw new IOException("Certificates store format error");
        }
        String host = bin.readLine();
        byte[] cert_data = hexDecode(bin.readLine());
        cert_map.put(host, new LocalCertificateData(cert_data));
      }
    }
  }

  /**
   * Saves all the certificates we have stored in the map.
   * 
   * @throws IOException 
   */
  private void saveLocalCertificates() throws IOException {
    File user_home_file = getLocalCertsFile();
    user_home_file.delete();
    try (PrintWriter bout = new PrintWriter(new BufferedWriter(
            new OutputStreamWriter(
                      new FileOutputStream(user_home_file), "UTF-8")))) {
      Set<Entry<String, LocalCertificateData>> entries = cert_map.entrySet();
      for (Entry<String, LocalCertificateData> entry : entries) {
        bout.println("1");
        bout.println(entry.getKey());
        bout.println(hexEncode(entry.getValue().getEncodedData()));
      }
    }
  }

  /**
   * Permits the given certificate for the given host. This stores the
   * certificate encoding in a user directory so that future requests on the
   * host will be permitted, and any change made to the certificate will be
   * notified (to help prevent man in the middle attacks).
   * 
   * @param host
   * @param cert
   * @throws java.io.IOException
   */
  public void userPermitCertificate(String host, Certificate cert)
                                                          throws IOException {
    try {
      cert_map.put(host, new LocalCertificateData(cert.getEncoded()));
    }
    catch (CertificateEncodingException ex) {
      throw new RuntimeException(ex);
    }
    saveLocalCertificates();
  }

  /**
   * Returns a HttpPost with the given arguments encoded into a FORM post
   * entity.
   * 
   * @param app_uri the address of the HTML application to post the form data
   *   to.
   * @param args the parameter arguments.
   * @return
   * @throws UnsupportedEncodingException 
   */
  public HttpPost createHttpFormPost(URI app_uri, Map<String, String> args)
                                        throws UnsupportedEncodingException {
    HttpPost sa_request = new HttpPost(app_uri);
    List <NameValuePair> sa_args = new ArrayList();
    for (Entry<String, String> entry : args.entrySet()) {
      sa_args.add(new BasicNameValuePair(entry.getKey(), entry.getValue()));
    }
    sa_request.setEntity(new UrlEncodedFormEntity(sa_args));
    return sa_request;
  }

  /**
   * Executes an HttpPost with the expectation that the response is a JSON
   * object when the response status code is '200'. If the response code
   * is not '200' then throws a HttpStatusCodeException.
   * 
   * @param post
   * @return
   * @throws IOException
   * @throws JSONException
   * @throws HttpStatusCodeException 
   */
  public JSONObject postWithJSONResult(HttpPost post)
                  throws IOException, JSONException, HttpStatusCodeException {

    CloseableHttpResponse response = httpclient.execute(post);
    StatusLine status_line = response.getStatusLine();
    int status_code = status_line.getStatusCode();

    // OK status code
    if (status_code == 200) {
      HttpEntity entity = response.getEntity();
      if (entity != null) {

        // Convert the response text content into a JSONObject.
        ContentType contentType = ContentType.getOrDefault(entity);
        Charset charset = contentType.getCharset();
        Reader reader = new BufferedReader(
                    new InputStreamReader(entity.getContent(), charset));
        JSONTokener json_tokener = new JSONTokener(reader);
        JSONObject ob = new JSONObject(json_tokener);
        
        // Close it before returning.
        response.close();
        
        return ob;
      }
      else {
        throw new IOException("null entity");
      }
    }
    else {
      throw new HttpStatusCodeException(status_line);
    }
  }

  /**
   * Returns a list of LocalCertificateData objects permitted for this
   * host.
   * 
   * @param host
   * @return 
   */
  public List<LocalCertificateData> getLocalCertificateInfo(String host) {
    LocalCertificateData data = cert_map.get(host);
    return (data == null) ?
                  Collections.EMPTY_LIST : Collections.singletonList(data);
  }
  
  
  // -----
  
  /**
   * Custom host name verification.
   */
  private class CustomHostnameVerifier implements HostnameVerifier {

    private final HostnameVerifier delegate = new DefaultHostnameVerifier();

    @Override
    public boolean verify(String host, SSLSession session) {

      try {
        // Is it self signed?
        Certificate[] certs = session.getPeerCertificates();
        if (certs.length == 1) {

          Certificate cert = certs[0];
          
          List<LocalCertificateData> local_certs =
                                              getLocalCertificateInfo(host);
          if (local_certs.isEmpty()) {
            // If no current cert,
            throw new SelfSignedCertificateException(
                                          FailType.CERT_UNKNOWN, host, cert);
          }
          // Does the local cert match the one we verifying?
          else {
            boolean one_match = false;
            for (LocalCertificateData data : local_certs) {
              if (data.matches(cert)) {
                one_match = true;
                break;
              }
            }
            // No certificates match
            if (!one_match) {
              // No, so generate a 'certificate changed' exception,
              throw new SelfSignedCertificateException(
                                          FailType.CERT_CHANGED, host, cert);
              
            }
          }

        }

      }
      catch (SSLException ex) {
        ex.printStackTrace(System.err);
        return false;
      }

      // Delegate verification to the default verifier.
      return delegate.verify(host, session);

    }

  }

}
