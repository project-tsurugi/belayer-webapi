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

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tsurugidb.belayer.webapi.dto.ExecStatus;
import com.tsurugidb.belayer.webapi.exception.ProcessExecException;
import com.tsurugidb.belayer.webapi.model.FileWatcher;
import com.tsurugidb.belayer.webapi.model.MonitoringManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class SessionKillExec {

  @Value("${webapi.tsurugi.conf}")
  String conf;

  @Value("${webapi.cli.cmd.session-kill}")
  String cmdString;

  @Autowired
  private MonitoringManager monitoringManager;

  public boolean killSession(String sessionId) {

    FileWatcher watcher = null;
    Path filePath = null;
    Path stdOutput = null;
    boolean success = false;
    try {
      Path tmpDirPath = Path.of(System.getProperty("java.io.tmpdir") + "/belayer-session-status");
      if (!Files.exists(tmpDirPath)) {
        Files.createDirectory(tmpDirPath);
      }

      String id = RandomStringUtils.randomAlphanumeric(8);
      filePath = tmpDirPath.resolve(String.format("monitoring-%s.log", id));
      stdOutput = tmpDirPath.resolve(String.format("stdout-%s.log", id));

      watcher = new FileWatcher(filePath);
      watcher.setCallback(status -> {
        if (status != null && ExecStatus.KIND_FINISH.equals(status.getKind())) {
          status.setFreezed(true);
        }
      });
      monitoringManager.addFileWatcher(watcher);

      var proc = runProcess(sessionId, filePath.toString(), stdOutput.toString());

      proc.waitFor();

      ExecStatus status = watcher.waitForExecStatus(s -> s != null && ExecStatus.KIND_FINISH.equals(s.getKind()));

      if (status != null) {
        success = ExecStatus.STATUS_SUCCESS.equals(status.getStatus());
        return success;
      }

      throw new ProcessExecException("session status is unknown.", null);

    } catch (IOException | InterruptedException ex) {
      throw new ProcessExecException("Process execution failed.", ex);
    } finally {
      if (watcher != null) {
        watcher.close();
      }
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

  public Process runProcess(String sessionId, String monitoringFile, String outFile) {
    String argsLine = String.format(cmdString, sessionId, monitoringFile, conf);
    String[] args = argsLine.split(" ");

    var pb = new ProcessBuilder(args);
    // stderr -> stdout
    pb.redirectErrorStream(true);
    // stdout -> file
    pb.redirectOutput(new File(outFile));
    log.debug("exec cmd: {}", Arrays.asList(args));
    try {
      var proc = pb.start();
      return proc;
    } catch (IOException ex) {
      throw new ProcessExecException("Process execution error caused.", ex);
    }

  }

}
