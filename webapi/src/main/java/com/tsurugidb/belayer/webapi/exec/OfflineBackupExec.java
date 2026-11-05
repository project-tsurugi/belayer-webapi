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
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

/**
 * Executor of the external command to back up Tsurugi DB offline.
 */
@Slf4j
@Component
public class OfflineBackupExec {

  @Value("${webapi.storage.root}")
  private String storageRootDir;

  @Value("${webapi.tsurugi.conf}")
  String conf;

  @Value("${webapi.cli.cmd.backupoffline}")
  String cmdString;

  @Autowired
  private MonitoringManager monitoringManager;

  /**
   * execute the command to back up Database offline.
   *
   * @param job Job
   * @return the given job
   * @throws ProcessExecException if the command failed (unless the job has been canceled)
   */
  public BackupJob backupOffline(BackupJob job) {
    FileWatcher watcher = null;
    StopWatch stopWatch = new StopWatch();

    try {
      Path tmpDir = Files.createTempDirectory(Constants.TEMP_DIR_PREFIX_MONITOR + job.getJobId() + "_");
      Path filePath = tmpDir.resolve(String.format("monitoring-%s.log", job.getJobId()));
      Path stdOutput = tmpDir.resolve(String.format("stdout-%s.log", job.getJobId()));

      watcher = new FileWatcher(filePath);
      watcher.setCallback(status -> {
        BigDecimal progress = status.getProgress();
        synchronized (job) {
          if (job.getStatus() != JobStatus.RUNNING) {
            // the job has already finished or been canceled. ignore lines written after that.
            return;
          }
          job.setProgress(
              progress.multiply(BigDecimal.valueOf(100), new MathContext(0, RoundingMode.HALF_UP)).intValue());
          job.setOutput(status.toStatusString());
        }
      });
      monitoringManager.addFileWatcher(watcher);

      String dirPath = job.getWorkDir().toString();

      stopWatch.start();

      Process proc = runProcess(dirPath, filePath, stdOutput, (String)job.getCredentials());
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
      // close the watcher first: stopWatch.stop() throws if the process has not been started.
      if (watcher != null) {
        watcher.close();
      }
      if (stopWatch.isRunning()) {
        stopWatch.stop();
      }
      log.debug("{}:{}ms", "exec backup offline", stopWatch.getTotalTimeMillis());
    }
  }

  /**
   * start the command process.
   *
   * @param dirPath path of the work directory of the job, passed to the command as the backup destination
   * @param monitoringFilePath path of the monitoring file the command writes its status to
   * @param outFile path of the file to write stdout and stderr of the command
   * @param token authentication token
   * @return started process
   * @throws ProcessExecException if failed to start the process
   */
  protected Process runProcess(String dirPath, Path monitoringFilePath, Path outFile, String token) {
    List<String> argList = CommandArguments.build(cmdString, dirPath, monitoringFilePath.toString(), conf);
    var pb = new ProcessBuilder(argList);
    // stderr -> stdout
    pb.redirectErrorStream(true);
    ScriptDebug.apply(pb, log);
    CommandArguments.setAuthToken(pb, token);
    // stdout -> file
    pb.redirectOutput(new File(outFile.toString()));
    log.debug("exec cmd: {}", CommandLineMask.mask(argList));
    try {
      var proc = pb.start();
      return proc;
    } catch (IOException ex) {
      throw new ProcessExecException("Process execution error caused.", ex);
    }
  }

  /**
   * Disposable that destroys the process if it is alive.
   */
  public static class DisposableProcess implements Disposable {

    private Process process;

    /**
     * Constructor.
     *
     * @param process process to dispose
     */
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
