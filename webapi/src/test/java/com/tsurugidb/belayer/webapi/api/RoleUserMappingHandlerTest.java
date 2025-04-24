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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonLocation;
import com.tsurugidb.belayer.webapi.config.RouterPath;
import com.tsurugidb.belayer.webapi.dto.ErrorResult;
import com.tsurugidb.belayer.webapi.security.RoleConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class RoleUserMappingHandlerTest {

  WebTestClient client;

  @MockBean
  RoleConfig roleConfig;

  @Autowired
  RouterFunction<ServerResponse> routerFunction;

  @BeforeEach
  public void setUp() {
    client = WebTestClient.bindToRouterFunction(routerFunction).configureClient().build();
  }

  @Test
  public void testShow_Success() {
    String result = "{\"ROLE_ADMIN:\".*\"\"";

    Mockito.when(roleConfig.dumpToJson()).thenReturn(result);

    client.get().uri(RouterPath.SHOW_ROLE_USER_MAPPING_API.getPath())
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .isEqualTo(result);
  }

  @Test
  public void testUpdate_Success() throws Exception {
    String param = "{\"ROLE_ADMIN:\".*\"\"";

    Mockito.doNothing().when(roleConfig).readFromJson(anyString());

    client.post().uri(RouterPath.UPDATE_ROLE_USER_MAPPING_API.getPath())
        .bodyValue(param)
        .exchange()
        .expectStatus().isOk()
        .expectBody(String.class)
        .isEqualTo("Success\n");
  }

  @Test
  public void testUpdate_Fail() throws Exception {
    String param = "{\"ROLE_ADMIN:\".*\"\"";

    JacksonException ex = new JacksonTestException("test");

    Mockito.doThrow(ex).when(roleConfig).readFromJson(anyString());

    client.post().uri(RouterPath.UPDATE_ROLE_USER_MAPPING_API.getPath())
        .bodyValue(param)
        .exchange()
        .expectStatus().isBadRequest()
        .expectBody(ErrorResult.class)
        .isEqualTo(new ErrorResult("Bad request format."));
  }

  public class JacksonTestException extends JacksonException {
    public JacksonTestException(String message) {
      super(message);
    }

    @Override
    public JsonLocation getLocation() {
      throw new UnsupportedOperationException("Unimplemented method 'getLocation'");
    }

    @Override
    public String getOriginalMessage() {
      throw new UnsupportedOperationException("Unimplemented method 'getOriginalMessage'");
    }

    @Override
    public Object getProcessor() {
      throw new UnsupportedOperationException("Unimplemented method 'getProcessor'");
    }
  }

}
