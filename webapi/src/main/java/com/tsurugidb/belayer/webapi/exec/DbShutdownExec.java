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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tsurugidb.belayer.webapi.dto.ExecStatus;
import com.tsurugidb.belayer.webapi.exception.ProcessExecException;
import com.tsurugidb.belayer.webapi.model.Constants;
import com.tsurugidb.belayer.webapi.model.FileWatcher;
import com.tsurugidb.belayer.webapi.model.MonitoringManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DbShutdownExec {

  @Value("${webapi.tsurugi.conf}")
  String conf;

  @Value("${webapi.cli.cmd.shutdown}")
  String cmdString;

  @Autowired
  private MonitoringManager monitoringManager;

  public void shutdownDatabase(String jobId) {

    FileWatcher watcher = null;

    try {
      Path tmpDir = Files.createTempDirectory(Constants.TEMP_DIR_PREFIX_MONITOR + jobId + "_");
      Path filePath = tmpDir.resolve(String.format("monitoring-%s.log", jobId));
      Path stdOutput = tmpDir.resolve(String.format("stdout-%s.log", jobId));

      watcher = new FileWatcher(filePath);
      watcher.setCallback(status -> {
        log.debug("file changed:" + status.toString());
        if (status != null && ExecStatus.KIND_DATA.equals(status.getKind())) {
          status.setFreezed(true);
        }
      });
      monitoringManager.addFileWatcher(watcher);

      var proc = runProcess(filePath.toString(), stdOutput.toString());

      proc.waitFor();

      ExecStatus status = watcher.waitForExecStatus(s -> s != null && ExecStatus.KIND_DATA.equals(s.getKind()));

      log.debug("status:" + status);
  
    } catch (IOException | InterruptedException ex) {
      throw new ProcessExecException("Process execution failed.", ex);
    } finally {
      if (watcher != null) {
        watcher.close();
      }
    }
  }

  public Process runProcess(String monitoringFile, String outFile) {
    String argsLine = String.format(cmdString, monitoringFile, conf);
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
