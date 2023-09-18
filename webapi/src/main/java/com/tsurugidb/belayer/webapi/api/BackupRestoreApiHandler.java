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

import java.nio.file.Path;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.tsurugidb.belayer.webapi.dto.BackupRestoreRequestParam;
import com.tsurugidb.belayer.webapi.dto.BackupRestoreStartRequestBody;
import com.tsurugidb.belayer.webapi.dto.Job;
import com.tsurugidb.belayer.webapi.dto.JobList;
import com.tsurugidb.belayer.webapi.dto.JobResult;
import com.tsurugidb.belayer.webapi.exception.BadRequestException;
import com.tsurugidb.belayer.webapi.exception.UnauthorizationException;
import com.tsurugidb.belayer.webapi.model.SystemTime;
import com.tsurugidb.belayer.webapi.security.UserTokenAuthentication;
import com.tsurugidb.belayer.webapi.service.BackupRestoreService;
import com.tsurugidb.belayer.webapi.service.FileSystemService;
import com.tsurugidb.belayer.webapi.service.JobIdService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class BackupRestoreApiHandler {

  @Autowired
  private BackupRestoreService backupRestoreService;

  @Autowired
  private JobIdService jobIdService;

  @Autowired
  FileSystemService fileSystemService;

  @Autowired
  SystemTime systemTime;

  /**
   * Start Backup API
   *
   * @param req Request
   * @return Response
   */
  public Mono<ServerResponse> requestBackup(ServerRequest req) {

    String jobId = jobIdService.createNewJobId();

    // // alternative way to obtain Authentication.
    //
    // String authorizationHeaderValue =
    // req.headers().firstHeader(HttpHeaders.AUTHORIZATION);
    //
    // var result = Mono.just(authorizationHeaderValue)
    // .flatMap(authService::checkAndCreateAuthentication)
    // .map(auth -> fillAuthenticationInfo(auth, saveDirPath, jobId))
    // .map(this::checkDir)
    // .flatMap(backupService::startBackup)
    // .map(this::createResult);

    Mono<JobResult> result = ReactiveSecurityContextHolder.getContext()
        .map(SecurityContext::getAuthentication)
        .flatMap(auth -> fillParams(auth, req, jobId))
        .map(this::checkDir)
        .flatMap(backupRestoreService::startBackup)
        .map(this::createResult);

    return ServerResponse.ok().body(
        BodyInserters.fromProducer(result, JobResult.class));
  }

  /**
   * Start Restore API
   *
   * @param req Request
   * @return Response
   */
  public Mono<ServerResponse> requestRestore(ServerRequest req) {

    String jobId = jobIdService.createNewJobId();

    Mono<JobResult> result = ReactiveSecurityContextHolder.getContext()
        .map(SecurityContext::getAuthentication)
        .flatMap(auth -> fillParams(auth, req, jobId))
        .map(this::checkFileExists)
        .map(this::checkFileDir)
        // exec restore
        .flatMap(backupRestoreService::startRestore)
        .map(this::createResult);

    return ServerResponse.ok().body(
        BodyInserters.fromProducer(result, JobResult.class));
  }

  private Mono<BackupRestoreRequestParam> fillParams(Authentication auth, ServerRequest req, String jobId) {

    Objects.requireNonNull(auth);
    if (auth instanceof UserTokenAuthentication) {
      var now = systemTime.now();
      var expirationTime = ((UserTokenAuthentication) auth).getTokenExpirationTime()
          .filter((it) -> now.isBefore(it))
          .orElseThrow(() -> new UnauthorizationException("Token is expired.", "Token is expired."));
      log.debug("exp:{}, now:{}", expirationTime, now);

    }

    return req.bodyToMono(BackupRestoreStartRequestBody.class)
        .switchIfEmpty(Mono.just(new BackupRestoreStartRequestBody()))
        .map(body -> {
          // fill params
          var param = new BackupRestoreRequestParam();
          param.setUid(auth.getName());
          param.setCredentials(auth.getCredentials());

          param.setDirPath(body.getDirPath());
          param.setZipFilePath(body.getZipFilePath());
          param.setJobId(jobId);
          return param;
        });

  }

  private BackupRestoreRequestParam checkDir(BackupRestoreRequestParam param) {

    // check dir path -> throw error
    fileSystemService.checkDirPath(param.getUid(), param.getDirPath());

    return param;
  }

  private BackupRestoreRequestParam checkFileDir(BackupRestoreRequestParam param) {

    // check file path -> throw error
    fileSystemService.checkDirPath(param.getUid(), Path.of(param.getZipFilePath()).getParent().toString());

    return param;
  }

  private BackupRestoreRequestParam checkFileExists(BackupRestoreRequestParam param) {

    // check file path -> throw error
    fileSystemService.checkFileExists(param.getUid(), param.getZipFilePath());

    return param;
  }

  private JobResult createResult(Job job) {
    var result = new JobResult();
    result.setJobId(job.getJobId());
    result.setUid(job.getUid());
    result.setType(job.getType());

    return result;
  }

  /**
   * Show specified backup job.
   *
   * @param request Request
   * @return Response
   */
  public Mono<ServerResponse> showBackupJobDetail(final ServerRequest request) {
    var jobId = request.pathVariable("jobid");
    var type = request.pathVariable("type");

    if (!Job.TYPE_BACKUP.equals(type) && !Job.TYPE_RESTORE.equals(type)) {
      var msg = "Invalid parameter. type:" + type;
      throw new BadRequestException(msg, msg);
    }

    return ReactiveSecurityContextHolder.getContext()
        .map(SecurityContext::getAuthentication)
        .map(Authentication::getName)
        .map(uid -> backupRestoreService.getJob(type, uid, jobId))
        .flatMap(job -> ServerResponse.ok().body(
            BodyInserters.fromProducer(job, Job.class)));
  }

  /**
   * Show list of the backup job.
   *
   * @param request Request
   * @return Response
   */
  public Mono<ServerResponse> listBackupJob(final ServerRequest request) {
    var type = request.pathVariable("type");

    if (!Job.TYPE_BACKUP.equals(type) && !Job.TYPE_RESTORE.equals(type)) {
      var msg = "Invalid parameter. type:" + type;
      throw new BadRequestException(msg, msg);
    }

    return ReactiveSecurityContextHolder.getContext()
        .map(SecurityContext::getAuthentication)
        .map(Authentication::getName)
        .flatMapMany(uid -> backupRestoreService.getJobList(type, uid))
        .collectList()
        .flatMap(job -> ServerResponse.ok().body(
            BodyInserters.fromProducer(Mono.just(new JobList(job)), JobList.class)));
  }

  /**
   * Cancel specified backup/restore job.
   *
   * @param request Request
   * @return Response
   */
  public Mono<ServerResponse> cancelJob(final ServerRequest request) {
    var jobId = request.pathVariable("jobid");
    var type = request.pathVariable("type");
    if (!Job.TYPE_BACKUP.equals(type) && !Job.TYPE_RESTORE.equals(type)) {
      var msg = "Invalid parameter. type:" + type;
      throw new BadRequestException(msg, msg);
    }
    return ReactiveSecurityContextHolder.getContext()
        .map(SecurityContext::getAuthentication)
        .map(Authentication::getName)
        .map(uid -> backupRestoreService.cancelBackupRestoreJob(type, uid, jobId))
        .flatMap(job -> ServerResponse.ok().body(
            BodyInserters.fromProducer(job, Job.class)));
  }

}
