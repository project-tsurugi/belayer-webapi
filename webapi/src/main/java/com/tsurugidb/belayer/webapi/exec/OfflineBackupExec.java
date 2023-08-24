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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

import com.tsurugidb.belayer.webapi.dto.BackupJob;
import com.tsurugidb.belayer.webapi.dto.ExecStatus;
import com.tsurugidb.belayer.webapi.dto.Job.JobStatus;
import com.tsurugidb.belayer.webapi.exception.ProcessExecException;
import com.tsurugidb.belayer.webapi.model.Constants;
import com.tsurugidb.belayer.webapi.model.FileWatcher;
import com.tsurugidb.belayer.webapi.model.MonitoringManager;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

@Slf4j
@Component
public class OfflineBackupExec {

  @Value("${webapi.storage.root}")
  private String storageRootDir;

  @Value("${webapi.tsurugi.conf}")
  String conf;

  @Value("${webapi.cli.cmd.backupoffline:oltp backup create %s --conf %s -v}")
  String cmdString;

  @Autowired
  private MonitoringManager monitoringManager;

  public BackupJob backupOffline(BackupJob job) {
    FileWatcher watcher = null;
    StopWatch stopWatch = new StopWatch();

    try {
      Path tmpDir = Files.createTempDirectory(Constants.TEMP_DIR_PREFIX_MONITOR + job.getJobId() + "_");
      Path filePath = tmpDir.resolve(String.format("monitoring-%s.log", job.getJobId()));

      watcher = new FileWatcher(filePath);
      watcher.setCallback(status -> job.setOutput(status.toStatusString()));
      monitoringManager.addFileWatcher(watcher);

      String dirPath = job.getWorkDir().toString();

      stopWatch.start();

      Process proc = runProcess(dirPath, filePath);
      // set process as Disposable into job.
      job.setDisposable(new DisposableProcess(proc));

      proc.waitFor();

      ExecStatus status = watcher.waitForExecStatus(s -> s != null && ExecStatus.KIND_FINISH.equals(s.getKind()));

      log.debug("status:" + status);
      if (status == null || !ExecStatus.STATUS_SUCCESS.equals(status.getStatus())) {
        if (job.getStatus() != JobStatus.CANCELED) {
          throw new ProcessExecException(
              "Process execution failed. exit status:" + proc.exitValue() + ", output:" + job.getOutput(), null);
        }
      }

      return job;

    } catch (IOException | InterruptedException ex) {
      throw new ProcessExecException("Process execution failed.", ex);
    } finally {
      stopWatch.stop();
      log.debug("{}:{}ms","exec backup offline", stopWatch.getTotalTimeMillis());
    }
  }

  public Process runProcess(String dirPath, Path monitoringFilePath) {
    String argsLine = String.format(cmdString, dirPath, monitoringFilePath.toString(), conf);
    String[] args = argsLine.split(" ");

    var argList = Arrays.asList(args);

    var pb = new ProcessBuilder(argList);
    pb.redirectErrorStream(true);
    log.debug("exec cmd: {}", argList);
    try {
      var proc = pb.start();
      return proc;
    } catch (IOException ex) {
      throw new ProcessExecException("Process execution error caused.", ex);
    }
  }

  public static class DisposableProcess implements Disposable {

    private Process process;

    public DisposableProcess(Process process) {
      this.process = process;
    }

    @Override
    public void dispose() {
      if (process.isAlive()) {
        process.destroy();
      }
    }

    @Override
    public boolean isDisposed() {
      return !process.isAlive();
    }
  }

}
