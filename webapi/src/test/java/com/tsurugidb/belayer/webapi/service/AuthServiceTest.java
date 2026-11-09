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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import com.tsurugidb.belayer.webapi.exception.UnauthenticatedException;
import com.tsurugidb.belayer.webapi.model.SystemTime;
import com.tsurugidb.belayer.webapi.security.PermissionConfig;
import com.tsurugidb.belayer.webapi.security.RoleConfig;
import com.tsurugidb.tsubakuro.auth.http.JwtTicketProvider;
import com.tsurugidb.tsubakuro.auth.http.TokenProvider;
import com.tsurugidb.tsubakuro.exception.CoreServiceCode;
import com.tsurugidb.tsubakuro.exception.CoreServiceException;

public class AuthServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-31T00:00:00Z");

  private AuthService authService;
  private TokenProvider tokenProvider;
  private SystemTime systemTime;

  @BeforeEach
  public void setUp() {
    tokenProvider = mock(TokenProvider.class);
    systemTime = mock(SystemTime.class);
    when(systemTime.now()).thenReturn(NOW);

    RoleConfig roleConfig = mock(RoleConfig.class);
    when(roleConfig.getRolesByUserId(anyString())).thenReturn(List.of("ROLE_ADMIN"));
    PermissionConfig permissionConfig = mock(PermissionConfig.class);
    when(permissionConfig.getDefaultRole()).thenReturn("ROLE_USER");

    authService = new AuthService();
    authService.ticketProvider = new JwtTicketProvider(tokenProvider);
    authService.roleConfig = roleConfig;
    authService.permissionConfig = permissionConfig;
    authService.systemTime = systemTime;
    authService.verifyCacheMaxMin = 30;
    authService.verifyTimeoutMs = 1000;
  }

  private static String token(String subject, Instant expiresAt) {
    var encoder = Base64.getUrlEncoder().withoutPadding();
    String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
    String payload = String.format(
        "{\"iss\":\"harinoki\",\"sub\":\"%s\",\"aud\":\"tsurugidb\",\"iat\":%d,\"exp\":%d,\"tsurugi/auth/name\":\"user1\"}",
        subject, expiresAt.getEpochSecond() - 300, expiresAt.getEpochSecond());
    return encoder.encodeToString(header.getBytes(StandardCharsets.UTF_8)) + "."
        + encoder.encodeToString(payload.getBytes(StandardCharsets.UTF_8)) + "."
        + encoder.encodeToString("signature".getBytes(StandardCharsets.UTF_8));
  }

  private Authentication authenticate(String token) {
    return authService.checkAndCreateAuthentication("Bearer " + token).block();
  }

  @Test
  public void test_verified_by_auth_server() throws Exception {
    String token = token("access", NOW.plus(Duration.ofMinutes(10)));

    Authentication auth = authenticate(token);

    assertTrue(auth.isAuthenticated());
    assertEquals("user1", auth.getPrincipal());
    verify(tokenProvider, times(1)).verify(token);
  }

  @Test
  public void test_rejected_by_auth_server() throws Exception {
    String token = token("access", NOW.plus(Duration.ofMinutes(10)));
    doThrow(new CoreServiceException(CoreServiceCode.BROKEN_CREDENTIAL, "invalid token"))
        .when(tokenProvider).verify(token);

    assertThrows(UnauthenticatedException.class, () -> authenticate(token));
  }

  @Test
  public void test_auth_server_not_available() throws Exception {
    String token = token("access", NOW.plus(Duration.ofMinutes(10)));
    doThrow(new IOException("connection refused")).when(tokenProvider).verify(token);

    assertThrows(UnauthenticatedException.class, () -> authenticate(token));
  }

  @Test
  public void test_expired_token() throws Exception {
    String token = token("access", NOW);

    assertThrows(UnauthenticatedException.class, () -> authenticate(token));
    verify(tokenProvider, never()).verify(anyString());
  }

  @Test
  public void test_refresh_token_is_not_accepted() throws Exception {
    String token = token("refresh", NOW.plus(Duration.ofHours(24)));

    assertThrows(UnauthenticatedException.class, () -> authenticate(token));
    verify(tokenProvider, never()).verify(anyString());
  }

  @Test
  public void test_cached_after_verified() throws Exception {
    String token = token("access", NOW.plus(Duration.ofMinutes(10)));

    authenticate(token);
    when(systemTime.now()).thenReturn(NOW.plus(Duration.ofMinutes(9)));
    authenticate(token);

    verify(tokenProvider, times(1)).verify(token);
  }

  @Test
  public void test_cache_expires_at_token_expiration() throws Exception {
    String token = token("access", NOW.plus(Duration.ofMinutes(10)));

    authenticate(token);
    when(systemTime.now()).thenReturn(NOW.plus(Duration.ofMinutes(10)));

    assertThrows(UnauthenticatedException.class, () -> authenticate(token));
    verify(tokenProvider, times(1)).verify(token);
  }

  @Test
  public void test_cache_expires_at_cache_limit() throws Exception {
    String token = token("access", NOW.plus(Duration.ofMinutes(60)));

    authenticate(token);
    when(systemTime.now()).thenReturn(NOW.plus(Duration.ofMinutes(29)));
    authenticate(token);
    verify(tokenProvider, times(1)).verify(token);

    when(systemTime.now()).thenReturn(NOW.plus(Duration.ofMinutes(30)));
    authenticate(token);
    verify(tokenProvider, times(2)).verify(token);
  }

  @Test
  public void test_cache_disabled() throws Exception {
    authService.verifyCacheMaxMin = 0;
    String token = token("access", NOW.plus(Duration.ofMinutes(10)));

    authenticate(token);
    authenticate(token);

    verify(tokenProvider, times(2)).verify(token);
  }

  @Test
  public void test_cached_token_is_rejected_after_cache_expired() throws Exception {
    String token = token("access", NOW.plus(Duration.ofMinutes(60)));

    authenticate(token);
    doThrow(new IOException("connection refused")).when(tokenProvider).verify(token);
    when(systemTime.now()).thenReturn(NOW.plus(Duration.ofMinutes(10)));
    authenticate(token);

    when(systemTime.now()).thenReturn(NOW.plus(Duration.ofMinutes(30)));
    assertThrows(UnauthenticatedException.class, () -> authenticate(token));
  }

  @Test
  public void test_auth_server_does_not_respond() throws Exception {
    authService.verifyTimeoutMs = 200;
    String token = token("access", NOW.plus(Duration.ofMinutes(10)));
    CountDownLatch interrupted = new CountDownLatch(1);
    doAnswer(invocation -> {
      try {
        Thread.sleep(10_000);
      } catch (InterruptedException ex) {
        interrupted.countDown();
        throw ex;
      }
      return null;
    }).when(tokenProvider).verify(token);

    long start = System.nanoTime();
    assertThrows(UnauthenticatedException.class, () -> authenticate(token));
    long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

    assertTrue(elapsedMs < 5_000, "waited " + elapsedMs + " ms");
    // the thread waiting for the response is released
    assertTrue(interrupted.await(5, TimeUnit.SECONDS));
    // a token not verified in time is not cached
    doNothing().when(tokenProvider).verify(token);
    authenticate(token);
    verify(tokenProvider, times(2)).verify(token);
  }

  @Test
  public void test_auth_server_responds_within_timeout() throws Exception {
    authService.verifyTimeoutMs = 2_000;
    String token = token("access", NOW.plus(Duration.ofMinutes(10)));
    doAnswer(invocation -> {
      Thread.sleep(100);
      return null;
    }).when(tokenProvider).verify(token);

    assertTrue(authenticate(token).isAuthenticated());
  }

  @Test
  public void test_timeout_disabled() throws Exception {
    authService.verifyTimeoutMs = 0;
    String token = token("access", NOW.plus(Duration.ofMinutes(10)));

    assertTrue(authenticate(token).isAuthenticated());

    String rejected = token("access", NOW.plus(Duration.ofMinutes(20)));
    doThrow(new CoreServiceException(CoreServiceCode.BROKEN_CREDENTIAL, "invalid token"))
        .when(tokenProvider).verify(rejected);
    assertThrows(UnauthenticatedException.class, () -> authenticate(rejected));
  }
}
