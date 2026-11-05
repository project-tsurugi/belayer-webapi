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

import java.util.regex.Pattern;

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

/**
 * Handler for the session control APIs.
 */
@Component
public class SessionControlApiHandler {

    private static final String NO_REQUEST_BODY = "Request body is required.";

    /**
     * Session ID: the numeric ID that tgctl takes after ":" (the session
     * commands of webapi.cli.cmd put the ":" in front).
     */
    static final Pattern SESSION_ID = Pattern.compile("[0-9]{1,20}");

    /**
     * Session variable name: Tsurugi builds the names from 0-9, a-z, "_" and
     * "." and compares them case insensitively.
     */
    static final Pattern VARIABLE_NAME = Pattern.compile("[0-9A-Za-z_.]{1,100}");

    /** Session variable value: printable ASCII, no control characters. */
    static final Pattern VARIABLE_VALUE = Pattern.compile("[\\x20-\\x7E]{1,100}");

    @Autowired
    SessionControlService sessionControlService;

    /**
     * reject a session ID that is not a numeric session ID.
     *
     * @param sessionId session ID from the request
     * @throws BadRequestException if the session ID is not valid
     */
    private static void checkSessionId(String sessionId) {
        if (sessionId == null || !SESSION_ID.matcher(sessionId).matches()) {
            var msg = "invalid session ID.";
            throw new BadRequestException(msg, msg);
        }
    }

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
                    checkSessionId(sessionId);
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
     * @return Response (400 Bad Request if the request body is empty)
     */
    public Mono<ServerResponse> setVariable(ServerRequest req) {

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    return req.bodyToMono(SessionVariable.class)
                            .switchIfEmpty(Mono.error(() -> new BadRequestException(NO_REQUEST_BODY,
                                    NO_REQUEST_BODY)))
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
                    checkSessionId(param.getSessionId());
                    if (!VARIABLE_NAME.matcher(param.getVarName()).matches()) {
                        var msg = "invalid variable name.";
                        throw new BadRequestException(msg, msg);
                    }
                    if (!VARIABLE_VALUE.matcher(param.getVarValue()).matches()) {
                        var msg = "invalid variable value.";
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
     * @return Response (400 Bad Request if the request body is empty)
     */
    public Mono<ServerResponse> killSession(ServerRequest req) {

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    return req.bodyToMono(SessionStatus.class)
                            .switchIfEmpty(Mono.error(() -> new BadRequestException(NO_REQUEST_BODY,
                                    NO_REQUEST_BODY)))
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
                    checkSessionId(param.getSessionId());

                    boolean success = sessionControlService.killSession(param.getSessionId(), param.getToken());
                    if (success) {
                        return ServerResponse.ok().build();
                    }
                    return ServerResponse.status(400)
                            .bodyValue(new ErrorResult("failed to kill session."));
                });
    }

}
