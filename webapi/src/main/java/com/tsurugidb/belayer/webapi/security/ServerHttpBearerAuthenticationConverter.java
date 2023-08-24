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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.tsurugidb.belayer.webapi.service.AuthService;

import reactor.core.publisher.Mono;

/**
 * AuthenticationConverter that verify Bearer token and convert Authentication object.
 */
@Component
public class ServerHttpBearerAuthenticationConverter implements ServerAuthenticationConverter {

  @Autowired
  AuthService authService;

  /**
   * {@inheritDoc}
   */
  @Override
  public Mono<Authentication> convert(ServerWebExchange serverWebExchange) {
    return Mono.justOrEmpty(serverWebExchange)
        .flatMap(this::extractAuthorizationHeader)
        .flatMap(authService::checkAndCreateAuthentication);
  }

  private Mono<String> extractAuthorizationHeader(ServerWebExchange serverWebExchange) {
    return Mono.justOrEmpty(serverWebExchange.getRequest()
        .getHeaders()
        .getFirst(HttpHeaders.AUTHORIZATION));
  }

}
