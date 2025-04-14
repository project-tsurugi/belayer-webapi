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
package com.tsurugidb.belayer.webapi.api;

import static org.mockito.ArgumentMatchers.anyString;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.tsurugidb.belayer.webapi.dto.AuthResult;
import com.tsurugidb.belayer.webapi.service.AuthService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class AuthHandlerTest {

  WebTestClient client;

  @MockBean
  AuthService authService;

  @Autowired
  RouterFunction<ServerResponse> routerFunction;

  @BeforeEach
  public void setUp() {
    client = WebTestClient.bindToRouterFunction(routerFunction).configureClient().build();
  }

  @Test
  public void testAuth_Success() {
    String userId = "user1";
    String rt = "refreshtoken";
    Instant rtExpirationTime = Instant.now();
    String at = "token";
    Instant atExpirationTime = Instant.now();

    AuthResult result = new AuthResult(userId, rt, rtExpirationTime, at, atExpirationTime, null, null, null);

    Mockito.when(authService.verifyCredential(anyString(), anyString())).thenReturn(result);

    MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
    map.add("uid", "aaa");
    map.add("pw", "123");

    client.post().uri("/api/auth")
        .bodyValue(map)
        .exchange()
        .expectStatus().isOk()
        .expectBody(AuthResult.class)
        .isEqualTo(result);
  }

  @Test
  public void testAuth_Fail() {
    AuthResult result = new AuthResult("user1", null, null, null, null, null, null,"auth failed.");
    Mockito.when(authService.verifyCredential(anyString(), anyString())).thenReturn(result);

    MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
    map.add("uid", "aaa");
    map.add("pw", "123");

    client.post().uri("/api/auth")
        .bodyValue(map)
        .exchange()
        .expectStatus().isBadRequest()
        .expectBody(AuthResult.class)
        .isEqualTo(result);
  }

  @Test
  public void testAuth_No_args() {
    AuthResult result = new AuthResult("user1", null, null, null, null, null, null, "auth failed.");
    Mockito.when(authService.verifyCredential(anyString(), anyString())).thenReturn(result);

    MultiValueMap<String, String> map = new LinkedMultiValueMap<>();

    client.post().uri("/api/auth")
        .bodyValue(map)
        .exchange()
        .expectStatus().isBadRequest()
        .expectBody(AuthResult.class)
        .isEqualTo(result);
  }

  @Test
  public void testRefreshToken_Success() {
    String rt = "refreshtoken";
    Instant rtExpirationTime = Instant.now();
    String at = "token";
    Instant atExpirationTime = Instant.now();

    AuthResult result = new AuthResult("user1", rt, rtExpirationTime, at, atExpirationTime, null, null, null);

    Mockito.when(authService.refreshToken(anyString())).thenReturn(result);

    MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
    map.add("rt", rt);

    client.post().uri("/api/refresh")
        .bodyValue(map)
        .exchange()
        .expectStatus().isOk()
        .expectBody(AuthResult.class)
        .isEqualTo(result);
  }

  @Test
  public void testRefreshToken_Fail() {
    var msg = "Token is exprired.";
    AuthResult result = new AuthResult("user1", null, null, null, null, null, null, msg);

    Mockito.when(authService.refreshToken(anyString())).thenReturn(result);

    MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
    map.add("rt", "dummy_rt");

    client.post().uri("/api/refresh")
        .bodyValue(map)
        .exchange()
        .expectStatus().isBadRequest()
        .expectBody(AuthResult.class)
        .isEqualTo(result);
  }

}
