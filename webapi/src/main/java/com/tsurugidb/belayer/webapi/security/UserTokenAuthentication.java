/*
 * Copyright 2023 tsurugi project.
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
package com.tsurugidb.belayer.webapi.security;

import java.time.Instant;
import java.util.Optional;

import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * Authentication that use access token.
 */
public class UserTokenAuthentication extends AbstractAuthenticationToken {

  private final String userId;
  private final String token;
  private final Optional<Instant> tokenExpirationTime;

  /**
   * {@inheritDoc}}
   */
  @Override
  public Object getCredentials() {
    return this.token;
  }

  /**
   * {@inheritDoc}}
   */
  @Override
  public Object getPrincipal() {
    return this.userId;
  }

  /**
   * Return token expiration time
   * 
   * @return token expiration time
   */
  public Optional<Instant> getTokenExpirationTime() {
    return tokenExpirationTime;
  }

  /**
   * Constructor.
   * 
   * This is used when user is unauthrorized.
   */
  public UserTokenAuthentication() {
    super(null);
    this.userId = null;
    this.token = null;
    this.tokenExpirationTime = null;
    super.setAuthenticated(false);
  }

  /**
   * Constructor.
   * 
   * @param userId              User ID
   * @param token               token
   * @param tokenExpirationTime token expiration time
   * @param authenticated       true if user is authenticated.
   */
  public UserTokenAuthentication(String userId, String token, Optional<Instant> tokenExpirationTime, boolean authenticated) {
    super(null);
    this.userId = userId;
    this.token = token;
    this.tokenExpirationTime = tokenExpirationTime;
    super.setAuthenticated(authenticated);
  }

}
