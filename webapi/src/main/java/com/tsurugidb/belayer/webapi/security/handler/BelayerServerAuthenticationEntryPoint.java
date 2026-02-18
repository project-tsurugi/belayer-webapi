package com.tsurugidb.belayer.webapi.security.handler;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class BelayerServerAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    @Override
    public Mono<Void> commence(ServerWebExchange swe, AuthenticationException ex) {
        var req = swe.getRequest();
        log.warn("authorization error: from={}, message={}", req.getRemoteAddress(), ex.toString());
        var res = swe.getResponse();
        res.setStatusCode(HttpStatus.UNAUTHORIZED);
        res.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "None");
        swe.mutate().response(res);
        return Mono.empty();
    }
}
