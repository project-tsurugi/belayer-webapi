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

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.Nonnull;
import javax.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import com.tsurugidb.belayer.webapi.config.FunctionPermission;
import com.tsurugidb.belayer.webapi.config.RouterPath;
import com.tsurugidb.belayer.webapi.exception.InvalidSettingException;

import io.netty.util.internal.ObjectUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * This holds role-permission mappings.
 */
@Configuration
@PropertySource("classpath:/config/permission.properties")
@ConfigurationProperties(prefix = "permission")
@Getter
@Setter
@Slf4j
public class PermissionConfig {

    // set up by properties file
    // key: permissions, value: set of roles
    private Map<String, Set<String>> config;

    // set up by properties file
    private String defaultRole;

    // set up by PostConstruct
    // key: role, value: set of permissions
    private Map<String, Set<String>> rolePermissionMap = new HashMap<>();

    private static Set<String> NOT_ASSIGNED = Set.of("==NOT_ASSIGNED==");

    /**
     * set up role permission Map
     *
     * @throws InvalidSettingException invalid Permission was set.
     */
    @PostConstruct
    public void setUpRolePermissionMap() throws InvalidSettingException {
        for (var permission : config.keySet()) {
            if (!isValidPermission(permission)) {
                throw new InvalidSettingException("Invalid permission was sepecified in permission.properties. permission:" + permission);
            }
        }

        Set<String> notAsignedSet = new HashSet<>();
        for (RouterPath path : RouterPath.values()) {
            for (var authz : path.getAuthorities()) {
                Set<String> roles = getRoles(authz);
                if (roles == NOT_ASSIGNED) {
                    notAsignedSet.add(authz.name());
                    continue;
                }
                // construct pairs of role and authz
                for (String role : roles) {
                    rolePermissionMap.computeIfAbsent(role, k -> new HashSet<>()).add(authz.name());
                }
            }
        }

        for (String notAsignedPermission : notAsignedSet) {
            log.warn("No role is assigned for permission:" + notAsignedPermission + ".");
        }
    }

    /**
     * returns default role name
     * 
     * @return default role name
     */
    public String getDefaultRole() {
        return defaultRole;
    }

    /**
     * Return role-permission mappings.
     * 
     * @return defined role-permission mappings
     */
    public Map<String, Set<String>> getRoleDefinition() {
        return Collections.unmodifiableMap(rolePermissionMap);
    }

    /**
     * Obtain authorities by role
     * 
     * @param role role name
     * @return Set of authorities
     */
    @Nonnull
    public Set<String> getAuthoritiesByRole(@Nonnull String role) {
        ObjectUtil.checkNotNull(role, "specified role is null.");

        return rolePermissionMap.getOrDefault(role, Set.of());
    }

    /**
     * check if specified role is valid or not.
     * 
     * @param role role name
     * @return true if role exists
     */
    public boolean isValidRole(@Nonnull String role) {
        ObjectUtil.checkNotNull(role, "specified role is null.");

        return rolePermissionMap.containsKey(role);
    }

    /**
     * Obtain set of role names by permission
     * 
     * @param permission permission
     * @return Set of role names that was assigned by permission
     */
    @Nonnull
    public Set<String> getRoles(@Nonnull FunctionPermission permission) {
        ObjectUtil.checkNotNull(permission, "authority is null.");

        if (permission == FunctionPermission.P_NONE) {
            return Set.of();
        }

        var roles = config.get(permission.name());
        if (roles == null) {
            return NOT_ASSIGNED;
        }

        return roles;
    }

    public boolean isValidPermission(@Nonnull String permission) {
        ObjectUtil.checkNotNull(permission, "specified role is null.");
        for (RouterPath path : RouterPath.values()) {
            for (var authz : path.getAuthorities()) {
                if (permission.equals(authz.name())) {
                    return true;
                }
            }
        }

        return false;
    }

}
