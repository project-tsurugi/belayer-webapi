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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tsurugidb.belayer.webapi.exception.InvalidSettingException;

import lombok.extern.slf4j.Slf4j;

/**
 * This holds role-user mappings.
 */
@Configuration
@Slf4j
public class RoleConfig {
    private static final String DUMP_FILE_NAME = "belayer_role_users.json";

    @Value("${webapi.user_role.data.directory}")
    private String dumpFileDir;

    @Value("${webapi.user_role.default.mapping}")
    private String defaultUserRoleMappingJsonString;

    @Autowired
    private PermissionConfig permissionConfig;

    @Autowired
    private ObjectMapper mapper;

    private String jsonFilePath;

    /**
     * Role to User Id Matcher Map
     * 
     * key: role name
     * value: Set of User ID matchers
     */
    Map<String, Set<String>> roleUserMap;

    /**
     * init role-user mapping
     *
     * @throws IOException
     */
    @PostConstruct
    synchronized public void init() throws InvalidSettingException {
        jsonFilePath = dumpFileDir + "/" + DUMP_FILE_NAME;

        String jsonString = readFromJsonFile();
        if (jsonString == null) {
            jsonString = defaultUserRoleMappingJsonString;
        }

        applyConfByJson(jsonString);
    }

    /**
     * Return User's roles
     * 
     * @param userId User ID
     * @return Set of role names
     */
    public synchronized List<String> getRolesByUserId(String userId) {
        List<String> roles = new ArrayList<>();
        for (var entry : roleUserMap.entrySet()) {
            var role = entry.getKey();
            var userConditions = entry.getValue();
            for (String userCondition : userConditions) {
                // regexp match
                if (userId.matches(userCondition)) {
                    roles.add(role);
                }
            }
        }
        return roles;
    }

    /**
     * Return role-permission mappings.
     * 
     * @return defined role-permission mappings
     */
    public synchronized Map<String, Set<String>> getRoleDefinition() {

        return permissionConfig.getRoleDefinition();
    }

    /**
     * return user-role mapping as JSON string.
     * 
     * @return user-role mapping JSON string.
     */
    public synchronized String dumpToJson() {
        try {
            return mapper.writeValueAsString(roleUserMap);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

    }

    /**
     * update user-role mapping from JSON string.
     * 
     * @param jsonString user-role mapping from JSON
     * @throws JacksonException mapping error
     */
    public synchronized void applyConfByJson(String jsonString) throws InvalidSettingException {

        log.debug(jsonString);
        try {
            Map<String, Set<String>> newRoleUserMap = mapper.readValue(jsonString,
                    new TypeReference<LinkedHashMap<String, Set<String>>>() {
                    });
            if (newRoleUserMap == null) {
                throw new InvalidSettingException("Invalid Setting. mapping:" + jsonString);
            }
            if (newRoleUserMap.size() == 0) {
                throw new InvalidSettingException("No roles assinged. mapping:" + jsonString);
            }

            for (var entry : newRoleUserMap.entrySet()) {
                var roleName = entry.getKey();

                if (!permissionConfig.isValidRole(roleName)) {
                    throw new InvalidSettingException("Invalid Role name. role name:" + roleName);
                }
            }
            roleUserMap = newRoleUserMap;

            // output if success
            dumpToJsonFile();

        } catch (JacksonException ex) {
            throw new InvalidSettingException("Invalid json expression.", ex);
        }
    }

    private synchronized String readFromJsonFile() {

        if (Files.exists(Path.of(jsonFilePath)) && !Files.isDirectory(Path.of(jsonFilePath))) {
            try {
                String jsonString = Files.readString(Path.of(jsonFilePath));
                return jsonString;
            } catch (IOException ex) {
                try {
                    Files.delete(Path.of(jsonFilePath));
                } catch (IOException ignore) {
                    // ignore
                }
                return null;
            }
        }

        return null;
    }

    private synchronized void dumpToJsonFile() {
        var jsonString = dumpToJson();
        try {
            var parent = Path.of(jsonFilePath).getParent();
            if (!Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.writeString(Path.of(jsonFilePath), jsonString);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

}
