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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tsurugidb.belayer.webapi.dto.DbStatus;
import com.tsurugidb.belayer.webapi.exception.ProcessExecException;

import lombok.extern.slf4j.Slf4j;

/**
 * Executor of the external command to obtain the status of Tsurugi DB.
 */
@Slf4j
@Component
public class DbStatusExec {

  @Value("${webapi.tsurugi.conf}")
  String conf;

  @Value("${webapi.cli.cmd.db-status}")
  String cmdString;

  @Value("${webapi.cli.cmd.tgctl_command}")
  String tgctl;

  @Value("${webapi.cli.cmd.tgha_command}")
  String tgha;

  @Autowired
  private ObjectMapper mapper;

  /**
   * obtains database status
   * @param jobId Job ID (not used)
   * @param authToken authentication token
   * @return status
   */
  public DbStatus getStatus(String jobId, String authToken) {

    Path filePath = null;
    try {
      Path tmpDirPath = Path.of(System.getProperty("java.io.tmpdir") + "/belayer-db-status");
      // createDirectories does not fail when another request has just created it.
      Files.createDirectories(tmpDirPath);

      String id = RandomStringUtils.randomAlphanumeric(8);
      filePath = tmpDirPath.resolve(String.format("monitoring-%s.log", id));

      var result = runProcess(filePath.toString(), authToken);

      return result;
    } catch (IOException ex) {
      throw new ProcessExecException("Process execution failed.", ex);
    } finally {
      // remove the monitoring file whether the command succeeded or not.
      if (filePath != null) {
        try {
          Files.deleteIfExists(filePath);
        } catch (IOException ex) {
          log.warn("failed to delete file", ex);
        }
      }
    }
  }

  /**
   * run the command and parse its output (stdout and stderr) as JSON.
   *
   * @param monitoringFile path of the monitoring file passed to the command
   * @param authToken authentication token
   * @return status
   * @throws ProcessExecException if failed to run the command or to parse its output
   */
  public DbStatus runProcess(String monitoringFile, String authToken) {
    String argsLine = String.format(cmdString, tgctl, tgha, monitoringFile, conf, authToken);
    String[] args = argsLine.split(" ");

    var pb = new ProcessBuilder(args);
    // stderr -> stdout
    pb.redirectErrorStream(true);
    ScriptDebug.apply(pb, log);
    String result = null;
    try {
      var proc = pb.start();
      InputStream is = proc.getInputStream();
      result = StreamUtils.copyToString(is, StandardCharsets.UTF_8);

      return mapper.readValue(result, new TypeReference<DbStatus>() {
      });

    } catch (IOException ex) {
      throw new ProcessExecException("Process execution error caused. out:[" + result + "]", ex);
    }

  }

}
