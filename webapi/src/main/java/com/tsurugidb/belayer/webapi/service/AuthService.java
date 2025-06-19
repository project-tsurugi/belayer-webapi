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
package com.tsurugidb.belayer.webapi.service;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import com.tsurugidb.belayer.webapi.dto.AuthResult;
import com.tsurugidb.belayer.webapi.exception.UnauthenticatedException;
import com.tsurugidb.belayer.webapi.security.PermissionConfig;
import com.tsurugidb.belayer.webapi.security.RoleConfig;
import com.tsurugidb.belayer.webapi.security.UserTokenAuthentication;
import com.tsurugidb.tsubakuro.auth.Ticket;
import com.tsurugidb.tsubakuro.auth.TicketProvider;
import com.tsurugidb.tsubakuro.auth.TokenKind;
import com.tsurugidb.tsubakuro.exception.CoreServiceCode;
import com.tsurugidb.tsubakuro.exception.CoreServiceException;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Service to authenticate user.
 */
@Slf4j
@Component
public class AuthService {

  private static final String BEARER = "Bearer ";
  private static final Predicate<String> MATCH_BEARER_LENGTH_PREDICATE = authValue -> authValue.length() > BEARER
      .length();
  private static final Function<String, Mono<String>> ISOLATE_BEARER_VALUE_FUNC = authValue -> Mono
      .justOrEmpty(authValue.substring(BEARER.length()));

  @Autowired
  TicketProvider ticketProvider;

  @Autowired
  PermissionConfig permissionConfig;

  @Autowired
  RoleConfig roleConfig;

  @Value("${webapi.auth.at.expiration.min}")
  private int accessKeyExpirationMin;

  /**
   * verify user password credential and return access token.
   * 
   * @param userId   user Id
   * @param password Password
   * @return Authentication Result
   */
  public AuthResult verifyCredential(@Nonnull String userId, @Nonnull String password) {

    try {
      Ticket ticket = ticketProvider.issue(userId, password);
      var rt = ticket.getToken(TokenKind.REFRESH);

      var token = rt.orElseThrow(() -> new IllegalArgumentException("Invalid Ticket:" + ticket));
      Objects.requireNonNull(token);

      return refreshToken(token);

    } catch (CoreServiceException e) {
      String msg = null;
      CoreServiceCode code = e.getDiagnosticCode();
      if (code == CoreServiceCode.AUTHENTICATION_ERROR) {
        msg = "Authentication Error.";
      } else {
        msg = "Authentication Failed. Try Again.";
      }
      return new AuthResult(userId, null, null, null, null, null, null, msg);
    } catch (InterruptedException | IOException e) {
      String msg = "Authentication Failed. Try Again.";
      return new AuthResult(userId, null, null, null, null, null, null, msg);
    }
  }

  /**
   * refresh access token from refresh token.
   * 
   * @param refreshToken refresh token
   * @return Authentication Result
   */
  public AuthResult refreshToken(@Nonnull String refreshToken) {
    String userId = "";
    try {
      Ticket refreshTicket = ticketProvider.restore(refreshToken);
      userId = refreshTicket.getUserId();
      Optional<Instant> rtExpirationTime = refreshTicket.getRefreshExpirationTime();
      Ticket ticket = ticketProvider.refresh(refreshTicket, accessKeyExpirationMin, TimeUnit.MINUTES);
      Optional<String> at = ticket.getToken(TokenKind.ACCESS);
      var atExpirationTime = ticket.getAccessExpirationTime();

      Authentication auth = this.verifyToken(at.orElse(null));
      var roleSet = auth.getAuthorities().stream().map(item -> item.toString()).collect(Collectors.toSet());
      Set<String> authzSet = new HashSet<>();
      for (String role : roleSet) {
        var authz = permissionConfig.getAuthoritiesByRole(role);
        authzSet.addAll(authz);
      }

      return new AuthResult(userId, refreshToken, rtExpirationTime.orElse(null), at.orElse(null),
          atExpirationTime.orElse(null), roleSet, authzSet, null);
    } catch (IllegalArgumentException ex) {
      return new AuthResult(userId, null, null, null, null, null, null, "invalid token is specified.");
    } catch (CoreServiceException e) {
      String msg = null;
      CoreServiceCode code = e.getDiagnosticCode();
      if (code == CoreServiceCode.AUTHENTICATION_ERROR) {
        msg = code.getCodeNumber() + ":Authentication Error.";
      } else if (code == CoreServiceCode.REFRESH_EXPIRED) {
        msg = code.getCodeNumber() + ":Refresh Token is exprired.";
      } else {
        msg = code.getCodeNumber() + ":Authentication Failed.";
      }
      return new AuthResult(userId, null, null, null, null, null, null, msg);
    } catch (InterruptedException | IOException e) {
      String msg = "Authentication Failed. Try Again.";
      return new AuthResult(userId, null, null, null, null, null, null, msg);
    }
  }

  /**
   * verify access token and create Authentication.
   * 
   * @param authorizationHeader Authorizaiton header value
   * @return Authentication
   */
  public Mono<Authentication> checkAndCreateAuthentication(String authorizationHeader) {
    return Mono.just(authorizationHeader)
        .filter(MATCH_BEARER_LENGTH_PREDICATE)
        .flatMap(ISOLATE_BEARER_VALUE_FUNC)
        .map(this::verifyToken)
        .switchIfEmpty(Mono.just(new UserTokenAuthentication()));
  }

  private Authentication verifyToken(String token) {

    Objects.requireNonNull(token);

    Ticket ticket = null;
    try {
      ticket = ticketProvider.restore(token);
    } catch (IllegalArgumentException ex) {
      throw new UnauthenticatedException("Invalid token.");
    }
    var accessExpirationTime = ticket.getAccessExpirationTime();

    List<String> roles = roleConfig.getRolesByUserId(ticket.getUserId());
    List<SimpleGrantedAuthority> authorities;
    if (roles == null) {
      authorities = new ArrayList<>();
    } else {
      authorities = roles.stream().map(SimpleGrantedAuthority::new).collect(Collectors.toList());
    }
    authorities.add(new SimpleGrantedAuthority(permissionConfig.getDefaultRole()));

    for (SimpleGrantedAuthority authority : authorities) {
      log.debug("role:{}, authz:{}", authority.getAuthority(),
          permissionConfig.getAuthoritiesByRole(authority.getAuthority()));
    }

    return new UserTokenAuthentication(ticket.getUserId(), token, accessExpirationTime, true, authorities);
  }

}
