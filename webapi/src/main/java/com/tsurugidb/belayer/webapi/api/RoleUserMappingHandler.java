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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.tsurugidb.belayer.webapi.dto.ErrorResult;
import com.tsurugidb.belayer.webapi.exception.InvalidSettingException;
import com.tsurugidb.belayer.webapi.security.RoleConfig;

import reactor.core.publisher.Mono;

@Component
public class RoleUserMappingHandler {

  @Autowired
  RoleConfig roleConfig;

  /**
   * Show Role Definition.
   * 
   * @param req request
   * @return response
   */
  public Mono<ServerResponse> showRoleDefinition(ServerRequest req) {
    return ServerResponse.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(roleConfig.getRoleDefinition());
  }


  /**
   * Show Role-User mapping Definition.
   * 
   * @param req request
   * @return response
   */
  public Mono<ServerResponse> showMapping(ServerRequest req) {
    return ServerResponse.ok()
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(roleConfig.dumpToJson());
  }

  /**
   * Update Role-User mapping Definition.
   * 
   * @param req request
   * @return response
   */
  public Mono<ServerResponse> updateMapping(ServerRequest req) {

    return req.bodyToMono(String.class)
        .flatMap(json -> {
          try {
            roleConfig.applyConfByJson(json);
            return ServerResponse.ok()
                .contentType(MediaType.APPLICATION_JSON).bodyValue("Success\n");
          } catch (InvalidSettingException ex) {
            return ServerResponse.badRequest()
                .contentType(MediaType.APPLICATION_JSON).bodyValue(new ErrorResult("Bad request format. " + ex.getMessage()));
          }
        });
  }

}
