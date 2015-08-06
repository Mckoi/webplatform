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

import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;

/**
 * A runtime exception thrown when a self signed certificate can not be
 * verified locally with the host (the public key is not in the local
 * database).
 *
 * @author Tobias Downer
 */
public class SelfSignedCertificateException extends RuntimeException {

  private final FailType fail_type;
  private final String host;
  private final Certificate cert;

  public SelfSignedCertificateException(
                            FailType fail_type, String host, Certificate cert) {
    super();
    this.fail_type = fail_type;
    this.host = host;
    this.cert = cert;
  }



  public FailType getFailType() {
    return fail_type;
  }

  public String getHost() {
    return host;
  }

  public String getPublicKey() {
    try {
      return WebClient.hexEncode(cert.getEncoded());
    }
    catch (CertificateEncodingException ex) {
      // Shouldn't happen,
      throw new RuntimeException(ex);
    }
  }

  public Certificate getCert() {
    return cert;
  }

  @Override
  public String getMessage() {
    return host;
  }

  public enum FailType {
    CERT_UNKNOWN,   // The certificate is not known and there's no current cert
    CERT_CHANGED,   // The certificate is not known and there's a current cert
  }

}
