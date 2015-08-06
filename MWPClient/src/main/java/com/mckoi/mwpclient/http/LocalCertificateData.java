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

import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.Arrays;

/**
 * Information about a certificate stored in the user's account.
 *
 * @author Tobias Downer
 */
public class LocalCertificateData {

  private final byte[] cert_data_format;

  /**
   * Constructor.
   * 
   * @param data 
   */
  public LocalCertificateData(byte[] data) {
    cert_data_format = Arrays.copyOf(data, data.length);
  }
  
  /**
   * Returns the certificate data.
   * 
   * @return 
   */
  public byte[] getEncodedData() {
    return Arrays.copyOf(cert_data_format, cert_data_format.length);
  }


  /**
   * Returns true if the given certificate matches this local version.
   * 
   * @param cert
   * @return 
   */
  public boolean matches(Certificate cert) {
    try {
      byte[] cert_encoded_data = cert.getEncoded();
      return Arrays.equals(cert_data_format, cert_encoded_data);
    }
    catch (CertificateEncodingException ex) {
      throw new RuntimeException(ex);
    }
  }

}
