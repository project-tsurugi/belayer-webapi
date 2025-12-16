/*
 * Copyright 2025 tsurugi project.
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
package com.tsurugidb.belayer.webapi;

import java.io.IOException;
import java.util.List;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * Initializer for belayer.
 */
@Component
@Slf4j
public class BelayerInitializer {

    @Value("${webapi.required.java_majar_version}")
    int requiredJavaMajarVersion;

    @Value("${webapi.required.commands}")
    List<String> requiredCommands;

    /**
     * This initialization method is to check the required conditions.
     */
    @PostConstruct
    private void init() {
        log.info("check required environments.");

        // Java version
        var majarVer = Runtime.version().feature();
        if (majarVer < requiredJavaMajarVersion) {
            throw new IllegalStateException(String.format("Belayer requires Java%s or later.", requiredJavaMajarVersion));
        }

        // external commands
        for (String command : requiredCommands) {
            if (!isCommandAvailable(command)) {
                throw new IllegalStateException(String.format("Command %s is not available.", command));
            }
        }
    }

    public static boolean isCommandAvailable(String command) {
        ProcessBuilder pb = new ProcessBuilder("which", command);

        try {
            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = pb.start();
            int exitCode = process.waitFor();

            return exitCode == 0;

        } catch (IOException | InterruptedException e) {
            return false;
        }
    }
}
