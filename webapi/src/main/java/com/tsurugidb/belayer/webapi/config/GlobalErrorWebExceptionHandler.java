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
package com.tsurugidb.belayer.webapi.config;

import java.util.Map;

import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.autoconfigure.web.reactive.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.tsurugidb.belayer.webapi.dto.ErrorResult;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Order(-2)
public class GlobalErrorWebExceptionHandler extends AbstractErrorWebExceptionHandler {

    public GlobalErrorWebExceptionHandler(ErrorAttributes g, WebProperties webProperties,
            ApplicationContext applicationContext, ServerCodecConfigurer configurer) {
        super(g, webProperties.getResources(), applicationContext);
        super.setMessageWriters(configurer.getWriters());
        super.setMessageReaders(configurer.getReaders());
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), r -> {
            ErrorAttributeOptions eao = ErrorAttributeOptions.defaults();

            Map<String, Object> ea = getErrorAttributes(r,
                    eao.including(ErrorAttributeOptions.Include.EXCEPTION,
                            ErrorAttributeOptions.Include.MESSAGE,
                            ErrorAttributeOptions.Include.STACK_TRACE));

            // put error log if status code is 5xx.
            int code = getStatusCode(ea);
            if (code >= 500) {
                log.error(ea.toString());
            } else {
                log.debug(ea.toString());
            }

            return renderJsonResponse(ea);
        });
    }

    private int getStatusCode(Map<String, Object> ea) {
        return (int) ea.get("status");
    }

    private String getMessage(Map<String, Object> ea) {
        return (String) ea.get("message");
    }

    private Mono<ServerResponse> renderJsonResponse(Map<String, Object> ea) {

        int statusCode = getStatusCode(ea);
        String message = getMessage(ea);
        if (statusCode >= 500) {
            message = "Unexpected Error occurred.";
        }

        return ServerResponse.status(statusCode)
                .contentType(MediaType.APPLICATION_JSON)
                .body(BodyInserters.fromValue(new ErrorResult(message)));
    }
}