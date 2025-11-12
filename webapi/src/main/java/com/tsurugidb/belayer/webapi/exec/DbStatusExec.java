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
package com.tsurugidb.belayer.webapi.exec;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tsurugidb.belayer.webapi.dto.DbStatus;
import com.tsurugidb.belayer.webapi.exception.ProcessExecException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DbStatusExec {

  @Value("${webapi.tsurugi.conf}")
  String conf;

  @Value("${webapi.cli.cmd.db-status}")
  String cmdString;

  @Autowired
  private ObjectMapper mapper;

  public DbStatus getStatus(String jobId) {

    Path filePath = null;
    Path stdOutput = null;
    boolean success = false;
    try {
      Path tmpDirPath = Path.of(System.getProperty("java.io.tmpdir") + "/belayer-db-status");
      if (!Files.exists(tmpDirPath)) {
        Files.createDirectory(tmpDirPath);
      }

      String id = RandomStringUtils.randomAlphanumeric(8);
      filePath = tmpDirPath.resolve(String.format("monitoring-%s.log", id));
      stdOutput = tmpDirPath.resolve(String.format("stdout-%s.log", id));

      var result = runProcess(filePath.toString(), stdOutput.toString());

      return result;
    } catch (IOException ex) {
      throw new ProcessExecException("Process execution failed.", ex);
    } finally {
      if (success) {
        try {
          if (filePath != null) {
            Files.delete(filePath);
          }
          if (stdOutput != null) {
            Files.delete(stdOutput);
          }
        } catch (Exception ignore) {
          log.warn("failed to delete file", ignore);
        }
      }
    }
  }

  public DbStatus runProcess(String monitoringFile, String outFile) {
    String argsLine = String.format(cmdString, monitoringFile, conf);
    String[] args = argsLine.split(" ");

    var pb = new ProcessBuilder(args);
    // stderr -> stdout
    pb.redirectErrorStream(true);

    try {
      var proc = pb.start();
      InputStream is = proc.getInputStream();

      return mapper.readValue(is, new TypeReference<DbStatus>() {
      });

    } catch (IOException ex) {
      throw new ProcessExecException("Process execution error caused.", ex);
    }

  }

}
