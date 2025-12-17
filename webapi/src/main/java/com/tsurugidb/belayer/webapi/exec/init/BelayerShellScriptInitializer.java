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
package com.tsurugidb.belayer.webapi.exec.init;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.FileCopyUtils;

import lombok.extern.slf4j.Slf4j;

/**
 * Initializer for JAR contained shell scripts.
 */
@Profile({ "default", "production" })
@Component
@Slf4j
public class BelayerShellScriptInitializer {

    String locationPattern = "classpath*:scripts/*.sh";

    @Value("${webapi.cli.cmd.scriptdir}")
    Path scriptDir;

    /**
     * This initialization method is to extract shell scripts into temporary directory.
     * 
     * @throws IOException
     */
    @PostConstruct
    private void init() throws IOException {
        log.info("Initialization process for extract shell scripts starts.");

        // create dir for shell script
        if (!Files.exists(scriptDir)) {
            Files.createDirectories(scriptDir);
        }

        // write out shell script file in temporary directory
        for (String scriptFileName : listFilesInJar()) {
            log.debug("processing script file:" + scriptFileName);

            File tempScript = new File(scriptDir.toFile(), scriptFileName);
            try (OutputStream outputStream = new FileOutputStream(tempScript)) {
                InputStream inputStream = new ClassPathResource("scripts/" + scriptFileName).getInputStream();
                FileCopyUtils.copy(inputStream, outputStream);
            }

            // add permission
            if (!System.getProperty("os.name").toLowerCase().startsWith("win")) {
                Set<PosixFilePermission> perms = new HashSet<>();
                perms.add(PosixFilePermission.OWNER_READ);
                perms.add(PosixFilePermission.OWNER_WRITE);
                perms.add(PosixFilePermission.OWNER_EXECUTE);
                Files.setPosixFilePermissions(tempScript.toPath(), perms);
            }
        }
    }

    public List<String> listFilesInJar() throws IOException {

        ClassLoader cl = this.getClass().getClassLoader();
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver(cl);

        Resource[] resources = resolver.getResources(locationPattern);
        return Arrays.stream(resources).map(it -> it.getFilename()).collect(Collectors.toList());
    }
}
