package com.tsurugidb.belayer.webapi.security;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RoleConfig {

    /**
     * Role to User Id Matcher Map
     * 
     * key: role name
     * value: Set of User ID matchers
     */
    @Value("#{${webapi.authz.role.user.map}}")
    Map<String, Set<String>> roleUserMap;

    /**
     * UserId Matcher to role Map.
     * 
     * key: User ID matcher
     * value: set of role names
     */
    Map<String, Set<String>> userRole = new HashMap<>();

    @PostConstruct
    public void setUpUserRole() {
        for (var entry : roleUserMap.entrySet()) {
            var userConditions = entry.getValue();
            for (String userCondition : userConditions) {
                userRole.computeIfAbsent(userCondition, k -> new HashSet<>()).add(entry.getKey());
            }
        }
    }

    public List<String> getRolesByUserId(String userId) {
        List<String> result = new ArrayList<>();
        for (var entry : userRole.entrySet()) {
            var userCondition = entry.getKey();
            // regexp match
            if (userId.matches(userCondition)) {
                result.addAll(entry.getValue());
            }
        }
        return result;
    }

}
