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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import com.tsurugidb.belayer.webapi.dto.BackupJob;
import com.tsurugidb.belayer.webapi.dto.BackupRestoreRequestParam;
import com.tsurugidb.belayer.webapi.dto.Job;
import com.tsurugidb.belayer.webapi.dto.Job.JobStatus;
import com.tsurugidb.belayer.webapi.dto.RestoreJob;
import com.tsurugidb.belayer.webapi.exception.BadRequestException;
import com.tsurugidb.belayer.webapi.exception.IORuntimeException;
import com.tsurugidb.belayer.webapi.exception.InternalServerErrorException;
import com.tsurugidb.belayer.webapi.exception.NotFoundException;
import com.tsurugidb.belayer.webapi.exec.DbQuiesceExec;
import com.tsurugidb.belayer.webapi.exec.DbRestoreExec;
import com.tsurugidb.belayer.webapi.exec.DbStatusExec;
import com.tsurugidb.belayer.webapi.exec.OfflineBackupExec;
import com.tsurugidb.belayer.webapi.model.Constants;
import com.tsurugidb.belayer.webapi.model.JobManager;
import com.tsurugidb.belayer.webapi.model.ZipFileUtil;
import com.tsurugidb.tsubakuro.common.Session;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
public class BackupRestoreService {

  @Autowired
  private JobManager jobManager;

  @Autowired
  ThreadPoolTaskExecutor threadPoolTaskExecutor;

  @Autowired
  TsubakuroService tsubakuroService;

  @Autowired
  FileSystemService fileSystemService;

  @Autowired
  DbStatusExec dbStatusExec;

  @Autowired
  DbQuiesceExec dbQuiesceExec;

  @Autowired
  OfflineBackupExec offlineBackupExec;

  @Autowired
  DbRestoreExec dbRestoreExec;

  @Value("${webapi.tsurugi.session_timeout}")
  private long sessionTimeout;

  @Value("${webapi.backup.zipcompresslevel}")
  private int zipCompressLevel;

  @PostConstruct
  public void validateProperties() {
    if (zipCompressLevel < -1 || zipCompressLevel > 9) {
      throw new IllegalArgumentException("zipCompressLevel must be between 0 and 9 or -1.");
    }
  }

  /**
   * Execute backup.
   *
   * @param param backup request parameter
   * @return Job
   */
  public Mono<BackupJob> startBackup(BackupRestoreRequestParam param) {

    // determine online or offline and call each service
    if (dbStatusExec.isOnline(param.getJobId())) {
      return backupOnline(param);
    }

    return backupOffline(param);

  }

  private Mono<BackupJob> backupOnline(BackupRestoreRequestParam param) {

    log.debug("register job :" + param.toString());

    var job = createBackupJob(param);
    jobManager.registerJob(job);

    log.debug("backupOnline start :" + job.toString());

    Disposable disp = Mono.just(job)
        .map(tsubakuroService::createBackupTransaction)
        .flatMapMany(tsubakuroService::backupOnline)
        .parallel()
        .runOn(Schedulers.fromExecutor(threadPoolTaskExecutor))
        .map(ctx -> {
          String downloadPath = fileSystemService.copyTo(ctx.getTargetFilePath(), job.getWorkDir());
          Session session = ctx.getSession();
          try {
            log.debug("expand session timeout for {} minutes", this.sessionTimeout);
            session.updateExpirationTime(this.sessionTimeout, TimeUnit.MINUTES);
          } catch (IOException ex) {
            String msg = "I/O Error while update session expiration time.";
            throw new InternalServerErrorException(msg, ex);
          }
          return downloadPath;
        })
        .sequential()
        .collect(ZipFileUtil.collectAsZipFile(job.getWorkDir(),
            fileSystemService.convertToAbsolutePath(job.getUid(),
                job.getDestDir() + String.format("/backup-%s.zip", job.getJobId())).toString(),
            zipCompressLevel))
        .flatMap(result -> setBackupResult(param.getUid(), param.getJobId(), result))
        .flatMap(this::registerCompletedResult)
        .map(j -> {
          fileSystemService.deleteDirectoryWithContent(((BackupJob)j).getWorkDir());
          return j;
        })
        .onErrorResume(ex -> registerFailedResult(job, ex))
        .subscribeOn(Schedulers.fromExecutor(threadPoolTaskExecutor))
        .subscribe();

    log.debug("set disposable:" + param.toString());
    job.setDisposable(disp);

    return Mono.just(job);
  }

  private BackupJob createBackupJob(BackupRestoreRequestParam param) {

    var job = new BackupJob();
    job.setType(Job.TYPE_BACKUP);
    job.setUid(param.getUid());
    job.setCredentials(param.getCredentials());
    job.setJobId(param.getJobId());
    job.setDestDir(param.getDirPath());
    job.setStatus(JobStatus.RUNNING);

    Path tmpDirPath = fileSystemService.createTempDirectory(Constants.TEMP_DIR_PREFIX_BACKUP + param.getJobId());
    job.setWorkDir(tmpDirPath);
    return job;
  }

  private Mono<BackupJob> backupOffline(BackupRestoreRequestParam param) {
    log.debug("register job :" + param.toString());

    var job = createBackupJob(param);
    jobManager.registerJob(job);

    log.debug("backupOffline start :" + param.toString());

    Mono.just(job)
        .map(this::startBackupInOffline)
        .flatMapMany(this::getBackupFilePaths)
        .collect(ZipFileUtil.collectAsZipFile(job.getWorkDir(),
            fileSystemService.convertToAbsolutePath(job.getUid(),
                job.getDestDir() + String.format("/backup-%s.zip", job.getJobId())).toString(),
            zipCompressLevel))
        .flatMap(result -> setBackupResult(param.getUid(), param.getJobId(), result))
        .flatMap(this::registerCompletedResult)
        .map(j -> {
          fileSystemService.deleteDirectoryWithContent(((BackupJob)j).getWorkDir());
          return j;
        })
        .onErrorResume(ex -> registerFailedResult(job, ex))
        .subscribeOn(Schedulers.fromExecutor(threadPoolTaskExecutor))
        .subscribe();

    return Mono.just(job);
  }

  /**
   * Start backup offline.
   *
   * @param job Job
   * @return job
   */
  private BackupJob startBackupInOffline(BackupJob job) {

    // call "oltp quiesce"
    dbQuiesceExec.callQuiesce(job);

    // call "oltp backup"
    job = offlineBackupExec.backupOffline(job);

    return job;
  }

  private Flux<String> getBackupFilePaths(BackupJob job) {

    try {
      var stream = Files.walk(job.getWorkDir())
          .filter(p -> !Files.isDirectory(p))
          .map(p -> p.toString());

      return Flux.fromStream(stream);

    } catch (IOException ex) {
      throw new IORuntimeException("I/O Error while collect backup files.", ex);
    }
  }

  /**
   * Execute restore.
   *
   * @param param restore request parameter
   * @return Job
   */
  public Mono<RestoreJob> startRestore(BackupRestoreRequestParam param) {
    log.debug("register job :" + param.toString());

    // determine online or offline and call each service
    if (dbStatusExec.isOnline(param.getJobId())) {
      var msg = "DB is online.";
      throw new BadRequestException(msg, msg);
    }

    var job = createRestoreJob(param);
    jobManager.registerJob(job);

    log.debug("restore start :" + job.toString());

    Mono.just(job)
        .map(this::expandZipFile)
        .map(dbRestoreExec::startRestore)
        .flatMap(this::registerCompletedResult)
        .map(j -> {
          fileSystemService.deleteDirectoryWithContent(((RestoreJob)job).getWorkDir());
          return j;
        })
        .onErrorResume(ex -> registerFailedResult(job, ex))
        .subscribeOn(Schedulers.fromExecutor(threadPoolTaskExecutor))
        .subscribe();

    return Mono.just(job);
  }

  private RestoreJob createRestoreJob(BackupRestoreRequestParam param) {

    var job = new RestoreJob();
    job.setType(Job.TYPE_RESTORE);
    job.setUid(param.getUid());
    job.setCredentials(param.getCredentials());
    job.setJobId(param.getJobId());
    job.setZipFilePath(param.getZipFilePath());
    job.setStatus(JobStatus.RUNNING);

    return job;
  }

  private RestoreJob expandZipFile(RestoreJob job) {
    Path workDir = fileSystemService.createTempDirectory(Constants.TEMP_DIR_PREFIX_RESTORE + job.getJobId());
    Path zipFilePath = fileSystemService.convertToAbsolutePath(job.getUid(), job.getZipFilePath());
    ZipFileUtil.extractZipFile(workDir, zipFilePath);
    job.setWorkDir(workDir);
    return job;
  }

  /**
   * Return job.
   *
   * @param jobId Job ID
   * @return Job
   */
  public Mono<Job> getJob(String type, String uid, String jobId) {
    log.debug("jobId:" + uid + "-" + jobId);

    if (!(Job.TYPE_BACKUP.equals(type) || Job.TYPE_RESTORE.equals(type))) {
      throw new BadRequestException("Invalid type. type:" + type,
          "Invalid type. type:" + type, null);
    }

    var job = jobManager.getJob(type, uid, jobId)
        .orElseThrow(() -> new NotFoundException("Specified job is not found. jobId:" + jobId,
            "Specified job is not found. jobId:" + jobId, null));
    log.debug("job:" + job);

    return Mono.just(job);
  }

  /**
   * Return list of jobs.
   *
   * @param jobId JobId
   * @return List of Jobs
   */
  public Flux<Job> getJobList(String type, String uid) {

    if (!(Job.TYPE_BACKUP.equals(type) || Job.TYPE_RESTORE.equals(type))) {
      throw new BadRequestException("Invalid type. type:" + type,
          "Invalid type. type:" + type, null);
    }

    List<Job> jobList = jobManager.getAllJobs();

    return Flux.fromIterable(jobList)
        .filter(job -> job.getUid().equals(uid))
        .filter(job -> job.getType().equals(type));
  }

  /**
   * Cancel Backup/Restore job.
   *
   * @param jobId
   * @return Task status
   */
  public Mono<Job> cancelBackupRestoreJob(String type, String uid, String jobId) {
    log.debug("cancel jobId:" + uid + "-" + jobId);

    if (!(Job.TYPE_BACKUP.equals(type) || Job.TYPE_RESTORE.equals(type))) {
      throw new BadRequestException("Invalid type. type:" + type,
          "Invalid type. type:" + type, null);
    }

    var job = jobManager.cancelJob(type, uid, jobId);

    return Mono.justOrEmpty(job);
  }

  private Mono<Job> setBackupResult(String uid, String jobId, String savedFile) {
    var jobOp = jobManager.getJob(Job.TYPE_BACKUP, uid, jobId);
    if (jobOp.isEmpty()) {
      // very rare case
      throw new NotFoundException("Specified job is not found. jobId:" + jobId,
          "Specified job is not found. jobId:" + jobId, null);
    }

    var targetJob = (BackupJob) jobOp.get();
    Path downloadPath = fileSystemService.convertToDownloadPath(uid, savedFile);
    targetJob.setZipFilePath(downloadPath.toString());
    return Mono.just(targetJob);
  }

  private Mono<Job> registerCompletedResult(Job job) {
    log.debug("complete:" + job.toString());
    jobManager.updateJobStatus(job, JobStatus.COMPLETED, null);
    return Mono.just(job);
  }

  private Mono<Job> registerFailedResult(Job job, Throwable th) {
    log.error("error occured. jobId:" + job.getJobId(), th);
    if (Job.TYPE_BACKUP.equals(job.getType())) {
      var zipFileName = ((BackupJob)job).getDestDir() + String.format("/backup-%s.zip", job.getJobId());
      new File(zipFileName).delete();
    }
    jobManager.updateJobStatus(job, JobStatus.FAILED, th);
    return Mono.just(job);
  }

}
