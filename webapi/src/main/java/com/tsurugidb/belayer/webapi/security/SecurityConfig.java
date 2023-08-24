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
package com.tsurugidb.belayer.webapi.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

import com.tsurugidb.belayer.webapi.config.Router.ApiPath;

import reactor.core.publisher.Mono;

/**
 * Configuration for SpringSecurity.
 */
@Configuration
public class SecurityConfig {

  @Value("${belayer.adminpage.path}")
  String adminPagePath;

  @Value("${management.endpoints.web.base-path}")
  String managementPath;

  /**
   * SecurityWebFilterChain for this app.
   */
  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
      ServerHttpBearerAuthenticationConverter bearerAuthenticationConverter) {

    // Disable things in spring security.
    http.httpBasic().disable();
    http.formLogin().disable();
    http.csrf().disable();
    http.logout().disable();

    // Those that should be pass authentication.
    http.authorizeExchange().pathMatchers("/favicon.ico", managementPath + "/**", "/api/hello", adminPagePath, adminPagePath + "/**", ApiPath.AUTH_API, ApiPath.AUTH_REFRESH_API).permitAll();
    http.authorizeExchange().pathMatchers(HttpMethod.OPTIONS).permitAll();

    // Apply a auth filter to all /**
    http.authorizeExchange()
        .pathMatchers("/**")
        .authenticated()
        .and()
        .addFilterAt(bearerAuthenticationFilter(bearerAuthenticationConverter), SecurityWebFiltersOrder.AUTHENTICATION)
        .httpBasic().disable()
        .formLogin().disable()
        .csrf().disable()
        .logout().disable()
        .cors();

    return http.build();
  }

  /**
   * AuthenticationFilter for this app.
   *
   * @param bearerAuthenticationConverter converter for authentication
   * @return AuthenticationFilter
   */
  private AuthenticationWebFilter bearerAuthenticationFilter(ServerHttpBearerAuthenticationConverter bearerConverter) {

    ReactiveAuthenticationManager authManager = new BearerTokenReactiveAuthenticationManager();
    AuthenticationWebFilter authWebFilter = new AuthenticationWebFilter(authManager);
    authWebFilter.setServerAuthenticationConverter(bearerConverter);
    authWebFilter.setRequiresAuthenticationMatcher(ServerWebExchangeMatchers.pathMatchers("/**"));

    return authWebFilter;
  }

  /**
   * ReactiveAuthenticationManager used in AuthenticationFilter.
   */
  public static class BearerTokenReactiveAuthenticationManager implements ReactiveAuthenticationManager {

    @Override
    public Mono<Authentication> authenticate(Authentication authentication) {
      return Mono.just(authentication);
    }
  }

}
