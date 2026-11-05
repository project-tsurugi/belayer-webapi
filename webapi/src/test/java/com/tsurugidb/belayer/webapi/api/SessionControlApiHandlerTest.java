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
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.tsurugidb.belayer.webapi.service.DbControlService;
import com.tsurugidb.belayer.webapi.service.SessionControlService;

/**
 * The values the session APIs and the WAL sync API pass to external commands
 * are checked before anything runs.
 */
@ActiveProfiles({"common", "ut"})
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "webapi.db.mock.tablenames=demo")
public class SessionControlApiHandlerTest {

  private static final String TEST_USER = "test_user";

  WebTestClient client;

  @Autowired
  RouterFunction<ServerResponse> routerFunction;

  @MockBean
  SessionControlService sessionControlService;

  @MockBean
  DbControlService dbControlService;

  @BeforeEach
  public void setUp() throws IOException {
    client = WebTestClient
        .bindToRouterFunction(routerFunction)
        .apply(SecurityMockServerConfigurers.springSecurity())
        .configureClient()
        .build();
  }

  private WebTestClient.ResponseSpec setVariable(String sessionId, String name, String value) {
    return client.post()
        .uri("/api/session/set")
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("session_id", sessionId, "var_name", name, "var_value", value))
        .exchange();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void setVariableAcceptsValidValues() {
    when(sessionControlService.setVariable(any())).thenReturn(true);

    setVariable("12", "sql.plan_recording", "true").expectStatus().isOk();
    setVariable("12", "SQL.Plan_Recording", "a value with spaces ~!#").expectStatus().isOk();
    setVariable("12", "n".repeat(100), "v".repeat(100)).expectStatus().isOk();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void setVariableRejectsInvalidValues() {
    setVariable("12;id", "sql.plan_recording", "true").expectStatus().isBadRequest();
    setVariable(":12", "sql.plan_recording", "true").expectStatus().isBadRequest();
    setVariable("label-1", "sql.plan_recording", "true").expectStatus().isBadRequest();
    setVariable("12", "sql plan", "true").expectStatus().isBadRequest();
    setVariable("12", "sql;id", "true").expectStatus().isBadRequest();
    setVariable("12", "n".repeat(101), "true").expectStatus().isBadRequest();
    setVariable("12", "sql.plan_recording", "true\nid").expectStatus().isBadRequest();
    setVariable("12", "sql.plan_recording", "tab\there").expectStatus().isBadRequest();
    setVariable("12", "sql.plan_recording", "あ").expectStatus().isBadRequest();
    setVariable("12", "sql.plan_recording", "v".repeat(101)).expectStatus().isBadRequest();

    verify(sessionControlService, never()).setVariable(any());
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void statusAndKillCheckTheSessionId() {
    when(sessionControlService.isAvailable(anyString(), anyString())).thenReturn(true);
    when(sessionControlService.killSession(anyString(), anyString())).thenReturn(true);

    client.get().uri("/api/session/status/12").exchange().expectStatus().isOk();
    client.get().uri("/api/session/status/abc").exchange().expectStatus().isBadRequest();
    client.post().uri("/api/session/kill").contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("session_id", "12")).exchange().expectStatus().isOk();
    client.post().uri("/api/session/kill").contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("session_id", "$(id)")).exchange().expectStatus().isBadRequest();

    verify(sessionControlService, never()).isAvailable(org.mockito.ArgumentMatchers.eq("abc"), anyString());
    verify(sessionControlService, never()).killSession(org.mockito.ArgumentMatchers.eq("$(id)"), anyString());
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void syncChecksTheEndpoint() {
    client.post().uri("/api/db/sync").contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("from", "host;id")).exchange().expectStatus().isBadRequest();
    client.post().uri("/api/db/sync").contentType(MediaType.APPLICATION_JSON)
        .bodyValue(Map.of("from", "$(id)")).exchange().expectStatus().isBadRequest();

    verify(dbControlService, never()).synchronizeTransactionLog(anyString(), anyString(), anyString());
  }
}
