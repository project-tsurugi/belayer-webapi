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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.util.matcher.ServerWebExchangeMatchers;

import com.tsurugidb.belayer.webapi.config.FunctionAuthority;
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

  @Value("#{${webapi.authz.authz.role.map}}")
  Map<String, Set<String>> authRoleMap;

  @Value("#{${webapi.authz.role.user.map}}")
  Map<String, Set<String>> roleUserMap;

  @Value("${webapi.authz.role.default}")
  String defaultRole;

  Map<String, Set<String>> userRole = new HashMap<>();
  Map<String, Set<String>> roleAuthzMap = new HashMap<>();

  @PostConstruct
  public void setUpUserRole() {
    for (var entry : roleUserMap.entrySet()) {
      var userList = entry.getValue();
      for (String userId : userList) {
        userRole.computeIfAbsent(userId, k -> new HashSet<>()).add(entry.getKey());
      }
    }
  }

  public List<String> getRolesByUserId(String userId) {
    List<String> result = new ArrayList<>();
    for (var entry : userRole.entrySet()) {
      var userMatch = entry.getKey();
      // regexp match
      if (userId.matches(userMatch)) {
        result.addAll(entry.getValue());
      }
    }
    return result;
  }

  public Set<String> getAuthzByRole(String role) {
    return roleAuthzMap.get(role);
  }

  public String getDefaultRole() {
    return defaultRole;
  }

  /**
   * SecurityWebFilterChain for this app.
   */
  @Bean
  public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http,
      ServerHttpBearerAuthenticationConverter bearerAuthenticationConverter) {

    http = http.exceptionHandling()
        .authenticationEntryPoint((swe, e) -> Mono.fromRunnable(() -> {
          swe.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
        }))
        .accessDeniedHandler((swe, e) -> Mono.fromRunnable(() -> {
          swe.getResponse().setStatusCode(HttpStatus.FORBIDDEN);
        }))
        .and()
        .httpBasic().disable()
        .formLogin().disable()
        .csrf().disable()
        .logout().disable()
        .cors().disable()
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
      Set<String> roleSet = new HashSet<>();
      for (var authz : path.getAuthorities()) {
        Set<String> roles = getRoles(authz);
        if (roles == null) {
          continue;
        }
        roleSet.addAll(roles);

        // construct pairs of role and authz
        for (String role : roles) {
          roleAuthzMap.computeIfAbsent(role, k -> new HashSet<>()).add(authz.name());
        }
      }

      if (roleSet.size() == 0) {
        log.debug("---" + path.getPathMatch() + ":" + List.of(path.getAuthorities()) + ":Permit All");
        spec = spec.pathMatchers(match).permitAll();
      } else {
        log.debug("---" + path.getPathMatch() + ":" + List.of(path.getAuthorities()) + ":" + roleSet);
        spec = spec.pathMatchers(match).hasAnyAuthority(roleSet.toArray(new String[0]));
      }
    }

    // Apply a auth filter to all /**
    http = spec.pathMatchers("/**")
        .hasAuthority(defaultRole).and();

    return http.build();
  }

  private Set<String> getRoles(FunctionAuthority authority) {
    if (authority == null) {
      return null;
    }

    if (authority == FunctionAuthority.P_NONE) {
      return null;
    }

    return authRoleMap.getOrDefault(authority.name(), Set.of(defaultRole));
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
