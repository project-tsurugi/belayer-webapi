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

import com.tsurugidb.belayer.webapi.dto.BackupJob;
import com.tsurugidb.belayer.webapi.dto.ExecStatus;
import com.tsurugidb.belayer.webapi.exception.ProcessExecException;
import com.tsurugidb.belayer.webapi.model.Constants;
import com.tsurugidb.belayer.webapi.model.FileWatcher;
import com.tsurugidb.belayer.webapi.model.MonitoringManager;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DbQuiesceExec {

  @Value("${webapi.tsurugi.conf}")
  String conf;

  @Value("${webapi.cli.cmd.quiesce}")
  String cmdString;

  @Autowired
  private MonitoringManager monitoringManager;

  public void callQuiesce(BackupJob job) {
    FileWatcher watcher = null;

    try {
      Path tmpDir = Files.createTempDirectory(Constants.TEMP_DIR_PREFIX_MONITOR + job.getJobId() + "_");
      Path filePath = tmpDir.resolve(String.format("monitoring-%s.log", job.getJobId()));
      Path stdOutput = tmpDir.resolve(String.format("stdout-%s.log", job.getJobId()));

      watcher = new FileWatcher(filePath);
      watcher.setCallback(status -> job.setOutput("quiece:" + status.toStatusString()));
      monitoringManager.addFileWatcher(watcher);

      var label = job.getJobId();
      var proc = runProcess(filePath, label, stdOutput);

      proc.waitFor();

      ExecStatus status = watcher.waitForExecStatus(s -> s != null && ExecStatus.KIND_FINISH.equals(s.getKind()));

      log.debug("status:" + status);
      if (status != null && ExecStatus.STATUS_SUCCESS.equals(status.getStatus())) {
        return;
      }

      throw new ProcessExecException("Process execution failed. status:" + status, null);

    } catch (IOException |

        InterruptedException ex) {
      throw new ProcessExecException("Process execution failed.", ex);
    } finally {
      if (watcher != null) {
        watcher.close();
      }
    }
  }

  public Process runProcess(Path monitoringFilePath, String label, Path outFile) {
    String argsLine = String.format(cmdString, monitoringFilePath.toString(), conf, label);
    String[] args = argsLine.split(" ");

    var pb = new ProcessBuilder(args);
    // stderr -> stdout
    pb.redirectErrorStream(true);
    // stdout -> file
    pb.redirectOutput(new File(outFile.toString()));
    log.debug("exec cmd: {}", Arrays.asList(args));
    try {
      var proc = pb.start();
      return proc;
    } catch (IOException ex) {
      throw new ProcessExecException("Process execution error caused.", ex);
    }
  }

}
