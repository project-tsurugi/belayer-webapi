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
package com.tsurugidb.belayer.webapi.service;

import java.io.File;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tsurugidb.belayer.webapi.dto.InstanceInfo;

import lombok.extern.slf4j.Slf4j;

/**
 * Instance Info Service
 */
@Component
@Slf4j
public class InstanceInfoService {

    @Autowired
    private ObjectMapper mapper;

    @Value("${webapi.config.root}/instance_info.json")
    private String configPath;

    private long lastModified = 0;
    private InstanceInfo instanceInfo = null;

    /**
     * get instance info form the config file.
     *
     * @return instance info
     */
    public InstanceInfo getInstanceInfo() {

        try {
            File file = new File(configPath);
            if (!file.exists() || file.isDirectory()) {
                log.debug("config not found");
                this.instanceInfo = new InstanceInfo();
                return instanceInfo;
            }

            long lastModified = file.lastModified();
            if (this.lastModified < lastModified) {

                this.instanceInfo = mapper.readValue(file, new TypeReference<InstanceInfo>() {
                });

                this.lastModified = lastModified;
            }

        } catch (IOException ex) {
            log.debug("config error", ex);
            this.instanceInfo = new InstanceInfo();
        }

        return instanceInfo;
    }

}
