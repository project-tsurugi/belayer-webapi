package com.tsurugidb.belayer.webapi.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class RoleConfig {
    private static final String DUMP_FILE_NAME = "belayer_role_users.json";

    @Value("${webapi.user_role.data.directory}")
    private String dumpFileDir;

    @Value("${webapi.user_role.default.mapping}")
    private String defaultUserRoleMapping;

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
     * UserId Matcher to role Map.
     * 
     * key: User ID matcher
     * value: set of role names
     */
    Map<String, Set<String>> userRole = new HashMap<>();

    /**
     * init role-user mapping
     *
     * @throws IOException
     */
    @PostConstruct
    synchronized public void init() throws IOException {
        jsonFilePath = dumpFileDir + "/" + DUMP_FILE_NAME;

        readFromJsonFile();
    }

    void rebuildUserRole() {
        for (var entry : roleUserMap.entrySet()) {
            var userConditions = entry.getValue();
            for (String userCondition : userConditions) {
                userRole.computeIfAbsent(userCondition, k -> new HashSet<>()).add(entry.getKey());
            }
        }
    }

    /**
     * Return User's roles
     * 
     * @param userId User ID
     * @return Set of role names
     */
    public synchronized List<String> getRolesByUserId(String userId) {
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
     */
    public synchronized void readFromJson(String jsonString) {
        try {
            roleUserMap = mapper.readValue(jsonString,
                    new TypeReference<LinkedHashMap<String, Set<String>>>() {
                    });
            rebuildUserRole();
            dumpToJsonFile();

        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

    }

    private synchronized void readFromJsonFile() {

            if (Files.exists(Path.of(jsonFilePath)) && !Files.isDirectory(Path.of(jsonFilePath))) {
                try {
                    String jsonString = Files.readString(Path.of(jsonFilePath));
                    readFromJson(jsonString);
                } catch (IOException ex) {
                    try {
                        Files.delete(Path.of(jsonFilePath));
                    } catch (IOException ignore) {
                        // ignore
                    }
                }
            } else {
                // default setting
                readFromJson(defaultUserRoleMapping);
            }

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
