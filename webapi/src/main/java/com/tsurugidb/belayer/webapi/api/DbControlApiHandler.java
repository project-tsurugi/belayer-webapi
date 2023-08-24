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

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.tsurugidb.belayer.webapi.dto.TableNames;
import com.tsurugidb.belayer.webapi.service.TsubakuroService;

import reactor.core.publisher.Mono;

@Component
public class DbControlApiHandler {

    @Autowired
    TsubakuroService tsubakuroService;
  
    /**
     * Obtain list of table names.
     *
     * @param req Request
     * @return Response
     */
    public Mono<ServerResponse> getTableNames(ServerRequest req) {

        Mono<TableNames> result = ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> tsubakuroService.listTables((String)auth.getCredentials()))
                .map(tableNames -> new TableNames(tableNames));

        return ServerResponse.ok().body(
                BodyInserters.fromProducer(result, TableNames.class));
    }
}
