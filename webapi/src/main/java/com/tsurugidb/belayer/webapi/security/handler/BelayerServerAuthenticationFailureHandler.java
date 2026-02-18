package com.tsurugidb.belayer.webapi.security.handler;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
public class BelayerServerAuthenticationFailureHandler implements ServerAuthenticationFailureHandler {

    @Override
    public Mono<Void> onAuthenticationFailure(WebFilterExchange wfe, AuthenticationException ex) {
      var req = wfe.getExchange().getRequest();
      log.warn("authentication error: from={}, path={}, message={}", req.getRemoteAddress(), req.getPath(), ex.toString());
      var res = wfe.getExchange().getResponse();
      res.setStatusCode(HttpStatus.UNAUTHORIZED);
      res.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "None");
      wfe.getExchange().mutate().response(res);
      return Mono.empty();
    }
}
