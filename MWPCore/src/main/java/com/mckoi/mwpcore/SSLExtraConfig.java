/*
 * Copyright (C) 2000 - 2015 Tobias Downer
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * version 3 along with this program.  If not, see
 * <http://www.gnu.org/licenses/>.
 */

package com.mckoi.mwpcore;

/**
 * A container for extra SSL configuration details, such as cipher suites to
 * exclude.
 *
 * @author Tobias Downer
 */
public class SSLExtraConfig {

  private String[] cipher_suites_to_exclude = null;

  private Boolean renegotiation_allowed = null;

  private String[] excluded_protocols = null;

  void setCipherSuitesToExclude(String[] ciphers) {
    cipher_suites_to_exclude = ciphers;
  }

  void setRenegotiationAllowed(boolean v) {
    renegotiation_allowed = v;
  }

  void setExcludedProtocols(String[] protocols) {
    excluded_protocols = protocols;
  }

  public String[] getCipherSuitesToExclude() {
    return cipher_suites_to_exclude;
  }

  public Boolean getRenegotiationAllowed() {
    return renegotiation_allowed;
  }

  public String[] getExcludedProtocols() {
    return excluded_protocols;
  }

}
