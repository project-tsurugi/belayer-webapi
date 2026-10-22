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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.tsurugidb.belayer.webapi.config.RouterPath;
import com.tsurugidb.belayer.webapi.security.RoleConfig;
import com.tsurugidb.belayer.webapi.service.SessionControlService;

/**
 * A request without a body is rejected with 400, instead of 200 with no body (BW-6).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class EmptyRequestBodyTest {

  private static final String TEST_USER = "test_user";

  WebTestClient client;

  @Autowired
  RouterFunction<ServerResponse> routerFunction;

  @MockBean
  SessionControlService sessionControlService;

  @MockBean
  RoleConfig roleConfig;

  @BeforeEach
  public void setUp() {
    client = WebTestClient
        .bindToRouterFunction(routerFunction)
        .apply(SecurityMockServerConfigurers.springSecurity())
        .configureClient()
        .build();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testSetVariable_noBody() {
    client.post().uri(RouterPath.SET_SESSION_VAR_API.getPath())
        .exchange()
        .expectStatus().isBadRequest();
    verify(sessionControlService, never()).setVariable(any());
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testSetVariable_emptyJsonBody() {
    client.post().uri(RouterPath.SET_SESSION_VAR_API.getPath())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue("")
        .exchange()
        .expectStatus().isBadRequest();
    verify(sessionControlService, never()).setVariable(any());
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testKillSession_noBody() {
    client.post().uri(RouterPath.KILL_SESSION_API.getPath())
        .contentType(MediaType.APPLICATION_JSON)
        .exchange()
        .expectStatus().isBadRequest();
    verify(sessionControlService, never()).killSession(anyString(), anyString());
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testUpdateRoleUserMapping_noBody() throws Exception {
    client.post().uri(RouterPath.UPDATE_ROLE_USER_MAPPING_API.getPath())
        .exchange()
        .expectStatus().isBadRequest();
    verify(roleConfig, never()).applyConfByJson(anyString());
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testUpdateRoleUserMapping_emptyTextBody() throws Exception {
    client.post().uri(RouterPath.UPDATE_ROLE_USER_MAPPING_API.getPath())
        .contentType(MediaType.TEXT_PLAIN)
        .bodyValue("")
        .exchange()
        .expectStatus().isBadRequest();
    verify(roleConfig, never()).applyConfByJson(anyString());
  }
}
