package com.tsurugidb.belayer.webapi.security;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

import com.tsurugidb.belayer.webapi.config.FunctionPermission;
import com.tsurugidb.belayer.webapi.config.RouterPath;

import io.netty.util.internal.ObjectUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Configuration
@PropertySource("classpath:/config/permission.properties")
@ConfigurationProperties(prefix = "permission")
@Getter
@Setter
@Slf4j
public class PermissionConfig {

    private Map<String, Set<String>> config;

    private String defaultRole;

    private Map<String, Set<String>> rolePermissionMap = new HashMap<>();


    @PostConstruct
    public void setUpRolePermissionMap() {
        for (RouterPath path : RouterPath.values()) {
            for (var authz : path.getAuthorities()) {
              Set<String> roles = getRoles(authz);

              // construct pairs of role and authz
              for (String role : roles) {
                rolePermissionMap.computeIfAbsent(role, k -> new HashSet<>()).add(authz.name());
              }
            }
        }
    }

    public String getDefaultRole() {
        return defaultRole;
    }

    public Set<String> getAuthzByRole(String role) {
        return rolePermissionMap.get(role);
    }

    public Set<String> getRoles(FunctionPermission permission) {
        ObjectUtil.checkNotNull(permission, "authority is null.");

        if (permission == FunctionPermission.P_NONE) {
            return Set.of();
        }

        var roles = config.get(permission.name());
        if (roles == null) {
            log.warn("No role is assigned for permission:" + permission.name() + ".");
            return Set.of("==NOT_ASSIGNED==");
        }

        return roles;
    }

}
