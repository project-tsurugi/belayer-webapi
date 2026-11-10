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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
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
import com.tsurugidb.belayer.webapi.model.SystemTime;
import com.tsurugidb.belayer.webapi.security.PermissionConfig;
import com.tsurugidb.belayer.webapi.security.RoleConfig;
import com.tsurugidb.belayer.webapi.security.UserTokenAuthentication;
import com.tsurugidb.tsubakuro.auth.Ticket;
import com.tsurugidb.tsubakuro.auth.TicketProvider;
import com.tsurugidb.tsubakuro.auth.TokenKind;
import com.tsurugidb.tsubakuro.auth.http.JwtTicketProvider;
import com.tsurugidb.tsubakuro.exception.CoreServiceCode;
import com.tsurugidb.tsubakuro.exception.CoreServiceException;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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

  @Autowired
  SystemTime systemTime;

  @Value("${webapi.auth.at.expiration.min}")
  private int accessKeyExpirationMin;

  @Value("${webapi.auth.verify.cache.max.min}")
  int verifyCacheMaxMin;

  @Value("${webapi.auth.verify.timeout.ms}")
  long verifyTimeoutMs;

  /** access tokens verified by the authentication server, and the time until which they are trusted */
  private final Map<String, Instant> verifiedTokens = new ConcurrentHashMap<>();

  /** threads to ask the authentication server, so that a request can stop waiting at the timeout */
  private final ExecutorService verifyExecutor = Executors.newCachedThreadPool(runnable -> {
    Thread thread = new Thread(runnable, "auth-verify");
    thread.setDaemon(true);
    return thread;
  });

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

      // the access token has just been issued by the authentication server, so it is not verified again here
      var roleSet = resolveRoles(ticket.getUserId()).stream().map(item -> item.toString())
          .collect(Collectors.toSet());
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
        // verifying may call the authentication server, so keep it off the event loop
        .flatMap(token -> Mono.fromCallable(() -> verifyToken(token)).subscribeOn(Schedulers.boundedElastic()))
        .switchIfEmpty(Mono.just(new UserTokenAuthentication()));
  }

  private Authentication verifyToken(String token) {

    Objects.requireNonNull(token);

    Ticket ticket = null;
    try {
      ticket = ticketProvider.restore(token);
    } catch (RuntimeException ex) {
      throw new UnauthenticatedException("Invalid token.");
    }
    // restore() only decodes the token, so check the kind and the expiration here
    if (ticket.getToken(TokenKind.ACCESS).isEmpty()) {
      throw new UnauthenticatedException("Invalid token.");
    }
    var accessExpirationTime = ticket.getAccessExpirationTime();
    Instant now = systemTime.now();
    if (accessExpirationTime.isEmpty() || !now.isBefore(accessExpirationTime.get())) {
      throw new UnauthenticatedException("Token is expired.");
    }
    verifyByAuthServer(token, ticket, accessExpirationTime.get(), now);

    return new UserTokenAuthentication(ticket.getUserId(), token, accessExpirationTime, true,
        resolveRoles(ticket.getUserId()));
  }

  /**
   * Resolve the roles of the user: the roles assigned to the user and the default role.
   */
  private List<SimpleGrantedAuthority> resolveRoles(String userId) {
    List<String> roles = roleConfig.getRolesByUserId(userId);
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
    return authorities;
  }

  /**
   * Ask the authentication server whether the token was issued by it (signature, issuer and audience).
   * A verified token is cached until the earlier of the cache limit and its expiration time.
   * Nothing is done for providers without an authentication server (authmock).
   */
  private void verifyByAuthServer(String token, Ticket ticket, Instant expirationTime, Instant now) {
    if (!(ticketProvider instanceof JwtTicketProvider)) {
      return;
    }
    Instant cachedUntil = verifiedTokens.get(token);
    if (cachedUntil != null && now.isBefore(cachedUntil)) {
      return;
    }

    try {
      verifyWithTimeout((JwtTicketProvider) ticketProvider, ticket);
    } catch (CoreServiceException ex) {
      log.debug("token was rejected by the authentication server: {}", ex.getMessage());
      throw new UnauthenticatedException("Invalid token.");
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new UnauthenticatedException("Authentication server is not available.");
    } catch (IOException | RuntimeException ex) {
      log.warn("failed to verify token by the authentication server: {}", ex.toString());
      throw new UnauthenticatedException("Authentication server is not available.");
    }

    if (verifyCacheMaxMin > 0) {
      Instant limit = now.plus(Duration.ofMinutes(verifyCacheMaxMin));
      verifiedTokens.values().removeIf(until -> !now.isBefore(until));
      verifiedTokens.put(token, expirationTime.isBefore(limit) ? expirationTime : limit);
    }
  }

  /**
   * Ask the authentication server, waiting for its response at most the configured time.
   * The HTTP request built by tsubakuro has no response timeout, so the call runs on another thread,
   * which is interrupted when the time is up. A value of 0 or less waits without limit.
   */
  private void verifyWithTimeout(JwtTicketProvider provider, Ticket ticket)
      throws InterruptedException, IOException, CoreServiceException {
    if (verifyTimeoutMs <= 0) {
      provider.verify(ticket, TokenKind.ACCESS);
      return;
    }
    Future<Void> future = verifyExecutor.submit(() -> {
      provider.verify(ticket, TokenKind.ACCESS);
      return null;
    });
    try {
      future.get(verifyTimeoutMs, TimeUnit.MILLISECONDS);
    } catch (TimeoutException ex) {
      future.cancel(true);
      throw new IOException("authentication server did not respond within " + verifyTimeoutMs + " ms", ex);
    } catch (InterruptedException ex) {
      future.cancel(true);
      throw ex;
    } catch (ExecutionException ex) {
      Throwable cause = ex.getCause();
      if (cause instanceof CoreServiceException) {
        throw (CoreServiceException) cause;
      }
      if (cause instanceof IOException) {
        throw (IOException) cause;
      }
      if (cause instanceof RuntimeException) {
        throw (RuntimeException) cause;
      }
      throw new IOException(cause);
    }
  }

}
