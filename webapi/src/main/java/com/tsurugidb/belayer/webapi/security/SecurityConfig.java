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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.security.web.server.WebFilterExchange;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;
import org.springframework.web.server.ServerWebExchange;

import com.tsurugidb.belayer.webapi.config.RouterPath;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Configuration for SpringSecurity.
 */
@Configuration
@Slf4j
public class SecurityConfig {

  @Value("${belayer.adminpage.path}")
  String adminPagePath;

  @Value("${management.endpoints.web.base-path}")
  String managementPath;

  @Value("${webapi.config.allowed_origins}")
  List<String> allowedOrigins;

  @Value("${webapi.config.allowed_methods}")
  List<String> allowedMethods;

  @Value("${webapi.config.allowed_headers}")
  List<String> allowedHeaders;

  @Value("${webapi.config.allow_credentials}")
  boolean allowCredentials;

  @Autowired
  PermissionConfig permissionConfig;

  /**
   * SecurityWebFilterChain for this app.
   */
  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
      ServerHttpBearerAuthenticationConverter bearerAuthenticationConverter) {

    http = http.exceptionHandling()
        .authenticationEntryPoint(new ServerAuthenticationEntryPoint() {
          @Override
          public Mono<Void> commence(ServerWebExchange swe, AuthenticationException ex) {
            log.debug("authentication Entry Point error:");
            var res = swe.getResponse();
            res.setStatusCode(HttpStatus.UNAUTHORIZED);
            res.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "None");
            swe.mutate().response(res);
            return Mono.empty();
          }
        })
        .accessDeniedHandler((swe, e) -> Mono.fromRunnable(() -> {
          swe.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        }))
        .and()
        .httpBasic().disable()
        .formLogin().disable()
        .csrf().disable()
        .logout().disable()
        .cors(cors -> {
          cors.configurationSource(corsConfigurationSource());
        })
        .addFilterAt(bearerAuthenticationFilter(bearerAuthenticationConverter), SecurityWebFiltersOrder.AUTHENTICATION);

    // pathes that should be pass the authentication.
    var spec = http
        .authorizeExchange()
        .pathMatchers("/favicon.ico", managementPath + "/**", adminPagePath, adminPagePath + "/**")
        .permitAll()
        .pathMatchers(HttpMethod.OPTIONS).permitAll();

    // require authorities
    for (RouterPath path : RouterPath.values()) {
      String match = path.getPathMatch();
      Set<String> roleSet = Stream.of(path.getAuthorities())
          .flatMap(authority -> permissionConfig.getRoles(authority).stream())
          .collect(Collectors.toSet());

      if (roleSet.size() == 0) {
        log.info("---" + path.getPathMatch() + ", permission:" + List.of(path.getAuthorities()) + ", role:Permit_All");
        spec = spec.pathMatchers(match).permitAll();
      } else if (roleSet == PermissionConfig.NOT_ASSIGNED) {
        log.info("---" + path.getPathMatch() + ", permission:" + List.of(path.getAuthorities()) + ", role:Deny_All");
        spec = spec.pathMatchers(match).denyAll();
      } else {
        log.info("---" + path.getPathMatch() + ", permission:" + List.of(path.getAuthorities()) + ", role:" + roleSet);
        spec = spec.pathMatchers(match).hasAnyAuthority(roleSet.toArray(new String[0]));
      }
    }

    // Apply a auth filter to all /**
    http = spec.pathMatchers("/**")
        .hasAuthority(permissionConfig.getDefaultRole()).and();

    return http.build();
  }

  private CorsConfigurationSource corsConfigurationSource() {

    CorsConfiguration configuration = new CorsConfiguration();

    if (allowedOrigins.size() != 0) {
      configuration.setAllowedOrigins(allowedOrigins);
      configuration.setExposedHeaders(List.of("Content-Disposition"));
    }

    if (allowedMethods.size() != 0) {
      configuration.setAllowedMethods(allowedMethods);
    }

    if (allowedHeaders.size() != 0) {
      configuration.setAllowedHeaders(allowedHeaders);
    }

    configuration.setAllowCredentials(allowCredentials);

    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", configuration);

    return source;
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
    authWebFilter.setAuthenticationFailureHandler(new ServerAuthenticationFailureHandler() {
      @Override
      public Mono<Void> onAuthenticationFailure(WebFilterExchange wfe, AuthenticationException exception) {
        log.debug("onAuthenticationFailure error:");
        var res = wfe.getExchange().getResponse();
        res.setStatusCode(HttpStatus.UNAUTHORIZED);
        res.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, "None");
        wfe.getExchange().mutate().response(res);
        return Mono.empty();
      }
    });
    

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
