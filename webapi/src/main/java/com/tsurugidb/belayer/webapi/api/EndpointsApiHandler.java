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
package com.tsurugidb.belayer.webapi.api;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.tsurugidb.belayer.webapi.dto.Endpoints;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class EndpointsApiHandler {

  @Value("${webapi.config.root}/endpoints.csv")
  private String configPath;

  private long lastModified = 0;
  private String[] endpoints;

  public Mono<ServerResponse> listEndpoints(ServerRequest req) {
    Endpoints result;

    try {
      File file = new File(configPath);
      if (!file.exists() || file.isDirectory()) {
        return ServerResponse.ok().bodyValue(new Endpoints(getDefaultEndpoint(req)));
      }

      long lastModified = file.lastModified();
      if (this.lastModified < lastModified) {
        this.endpoints = Files.lines(Path.of(file.getAbsolutePath()))
            .map(line -> Arrays.stream(line.split(","))
                .map(String::trim)
                .collect(Collectors.toList()))
            .flatMap(list -> list.stream())
            .collect(Collectors.toList())
            .toArray(new String[0]);

        this.lastModified = lastModified;
      }
      result = new Endpoints(this.endpoints);

    } catch (IOException ex) {
      log.warn("error occurred while reading a config file. " + configPath, ex);
      result = new Endpoints(getDefaultEndpoint(req));
    }

    return ServerResponse.ok().bodyValue(result);
  }

  private String[] getDefaultEndpoint(ServerRequest req) {
    
    return new String[] {req.uri().toString().split("/api")[0]};
  }
}
