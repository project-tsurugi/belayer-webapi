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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.tsurugidb.belayer.webapi.dto.DbStatus;
import com.tsurugidb.belayer.webapi.dto.TableNames;
import com.tsurugidb.belayer.webapi.service.DbControlService;
import com.tsurugidb.belayer.webapi.service.TsubakuroService;

@ActiveProfiles("ut")
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "webapi.db.mock.tablenames=demo,foo,bar")
public class DbControlApiHandlerTest {

  WebTestClient client;

  @Autowired
  RouterFunction<ServerResponse> routerFunction;

  @Autowired
  TsubakuroService tsubakuroService;

  @MockBean
  DbControlService dbControlService;


  private static final String TEST_USER = "test_user";

  @BeforeEach
  public void setUp() throws IOException {
    client = WebTestClient
        .bindToRouterFunction(routerFunction)
        .apply(SecurityMockServerConfigurers.springSecurity())
        .configureClient()
        .build();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void startDatabase() {

    doNothing().when(dbControlService).startDatabase(anyString(), anyString());
    client.post()
        .uri("/api/db/start")
        .exchange()
        .expectStatus().isOk();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void shutdown() {

    doNothing().when(dbControlService).shutdownDatabase(anyString(), anyString());
    client.get()
        .uri("/api/db/status")
        .exchange()
        .expectStatus().isOk();
  }


  @Test
  @WithMockUser(username = TEST_USER)
  public void isOnline() {

    when(dbControlService.getStatus(anyString())).thenReturn("running");
    var expect = new DbStatus("running");
    client.get()
        .uri("/api/db/status")
        .exchange()
        .expectStatus().isOk()
        .expectBody(DbStatus.class)
        .isEqualTo(expect);
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testGetTableNames() {

    TableNames expect = new TableNames(List.of("demo", "foo", "bar"));

    client.get()
        .uri("/api/db/tablenames")
        .exchange()
        .expectStatus().isOk()
        .expectBody(TableNames.class)
        .isEqualTo(expect);
  }

}
