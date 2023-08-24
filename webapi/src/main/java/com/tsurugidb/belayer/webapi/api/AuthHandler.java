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

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.tsurugidb.belayer.webapi.dto.AuthResult;
import com.tsurugidb.belayer.webapi.service.AuthService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class AuthHandler {

  @Autowired
  AuthService authService;

  public Mono<ServerResponse> auth(ServerRequest req) {
    Mono<MultiValueMap<String, String>> authReq = req.body(BodyExtractors.toFormData());

    return authReq.flatMap(this::verifyAuthParam)
        .flatMap(result -> {
          log.debug("auth result:" + result.toString());
          if (result.getErrorMessage() != null) {
            return ServerResponse.status(HttpStatus.BAD_REQUEST)
                .bodyValue(result);
          }

          return ServerResponse.ok()
              .contentType(MediaType.APPLICATION_JSON)
              .bodyValue(result);
        });
  }

  public Mono<ServerResponse> refresh(ServerRequest req) {
    Mono<MultiValueMap<String, String>> authReq = req.body(BodyExtractors.toFormData());

    return authReq.flatMap(this::refreshToken)
        .flatMap(result -> {
          if (result.getErrorMessage() != null) {
            return ServerResponse.status(HttpStatus.BAD_REQUEST)
                .bodyValue(result);
          }

          return ServerResponse.ok()
              .contentType(MediaType.APPLICATION_JSON)
              .bodyValue(result);
        });
  }

  private Mono<AuthResult> verifyAuthParam(MultiValueMap<String, String> req) {

    List<String> uidParam = req.get("uid");
    String uid = !CollectionUtils.isEmpty(uidParam) ? uidParam.get(0) : "";
    uid = (uid == null) ? "" : uid;

    List<String> pwParam = req.get("pw");
    String pw = !CollectionUtils.isEmpty(pwParam) ? pwParam.get(0) : "";
    pw = (pw == null) ? "" : pw;

    AuthResult authResult = authService.verifyCredential(uid, pw);
    return Mono.just(authResult);
  }

  private Mono<AuthResult> refreshToken(MultiValueMap<String, String> req) {
    List<String> refreshTokenParam = req.get("rt");
    String rt =  !CollectionUtils.isEmpty(refreshTokenParam) ? refreshTokenParam.get(0) : "";
    rt = (rt == null) ? "" : rt;
    AuthResult authResult = authService.refreshToken(rt);
    return Mono.just(authResult);
  }
}
