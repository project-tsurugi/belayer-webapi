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
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.tsurugidb.belayer.webapi.dto.StartDbParam;
import com.tsurugidb.belayer.webapi.dto.TableNames;
import com.tsurugidb.belayer.webapi.service.DbControlService;
import com.tsurugidb.belayer.webapi.service.TsubakuroService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class DbControlApiHandler {

    @Autowired
    DbControlService dbControlService;

    @Autowired
    TsubakuroService tsubakuroService;

    /**
     * Start Tsurugi Database
     *
     * @param req Request
     * @return Response
     */
    public Mono<ServerResponse> startDatabase(ServerRequest req) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    return req.bodyToMono(StartDbParam.class)
                            .switchIfEmpty(Mono.just(new StartDbParam()))
                            .flatMap(param -> {
                                String mode = param.getMode();
                                if (mode == null) {
                                    mode = "standalone";
                                }
                                log.debug("db launch mode:" + mode);
                                dbControlService.startDatabase("start", (String) auth.getCredentials(), mode);
                                return ServerResponse.ok().build();
                            });
                });
    }

    /**
     * Shutdown Tsurugi Database
     *
     * @param req Request
     * @return Response
     */
    public Mono<ServerResponse> shutdownDatabase(ServerRequest req) {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> {
                    dbControlService.shutdownDatabase("shutdown", (String) auth.getCredentials());
                    return ServerResponse.ok().build();
                });

    }

    /**
     * Show Status of Tsurugi Database
     *
     * @param req Request
     * @return Response
     */
    public Mono<ServerResponse> getStatus(ServerRequest req) {
        var status = dbControlService.getStatus("status");
        return ServerResponse.ok().body(BodyInserters.fromValue(status));
    }

    /**
     * Obtain list of table names.
     *
     * @param req Request
     * @return Response
     */
    public Mono<ServerResponse> getTableNames(ServerRequest req) {

        Mono<TableNames> result = ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> tsubakuroService.listTables((String) auth.getCredentials()))
                .map(tableNames -> new TableNames(tableNames));

        return ServerResponse.ok().body(
                BodyInserters.fromProducer(result, TableNames.class));
    }
}
