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
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.tsurugidb.belayer.webapi.dto.ErrorResult;
import com.tsurugidb.belayer.webapi.dto.SessionStatus;
import com.tsurugidb.belayer.webapi.dto.SessionVariable;
import com.tsurugidb.belayer.webapi.exception.BadRequestException;
import com.tsurugidb.belayer.webapi.service.SessionControlService;

import reactor.core.publisher.Mono;

@Component
public class SessionControlApiHandler {

    @Autowired
    SessionControlService sessionControlService;

    /**
     * Show Status of Tsurugi Session
     *
     * @param req Request
     * @return Response
     */
    public Mono<ServerResponse> getStatus(ServerRequest req) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    String sessionId = req.pathVariable("session_id");
                    boolean available = sessionControlService.isAvailable(sessionId, (String)auth.getCredentials());

                    String status = available ? "available" : "unavailable";
                    return ServerResponse.ok()
                            .body(BodyInserters.fromValue(new SessionStatus(sessionId, status, null, null)));
                });
    }

    /**
     * Set variable to Tsurugi Session
     *
     * @param req Request
     * @return Response
     */
    public Mono<ServerResponse> setVariable(ServerRequest req) {

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    return req.bodyToMono(SessionVariable.class)
                            .map(param -> {
                                param.setToken((String) auth.getCredentials());
                                return param;
                            });
                })
                .flatMap(param -> {
                    if (!StringUtils.hasLength(param.getSessionId()) || !StringUtils.hasLength(param.getVarName())
                            || !StringUtils.hasLength(param.getVarValue())) {
                        var msg = "invalid parameters.";
                        throw new BadRequestException(msg, msg);
                    }
                    var result = sessionControlService.setVariable(param);
                    if (result) {
                        return ServerResponse.ok()
                                .body(BodyInserters
                                        .fromValue(new SessionStatus(param.getSessionId(), null, param.getVarName(),
                                                null)));
                    }
                    var msg = "unable to set variable to session :" + param.getSessionId() + ". (name:"
                            + param.getVarName()
                            + ", value:" + param.getVarValue() + ")";
                    throw new BadRequestException(msg, msg, null);
                });

    }

    /**
     * Kill Tsurugi Session
     *
     * @param req Request
     * @return Response
     */
    public Mono<ServerResponse> killSession(ServerRequest req) {

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    return req.bodyToMono(SessionStatus.class)
                            .map(param -> {
                                param.setToken((String) auth.getCredentials());
                                return param;
                            });
                })
                .flatMap(param -> {
                    if (!StringUtils.hasLength(param.getSessionId())) {
                        var msg = "sessionId is not specified.";
                        throw new BadRequestException(msg, msg);
                    }

                    boolean success = sessionControlService.killSession(param.getSessionId(), param.getToken());
                    if (success) {
                        return ServerResponse.ok().build();
                    }
                    return ServerResponse.status(400)
                            .bodyValue(new ErrorResult("failed to kill session."));
                });
    }

}
