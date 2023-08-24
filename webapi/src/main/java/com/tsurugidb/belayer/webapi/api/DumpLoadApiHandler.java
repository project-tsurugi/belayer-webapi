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

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.tsurugidb.belayer.webapi.api.helper.LoadHelper;
import com.tsurugidb.belayer.webapi.dto.DumpJob;
import com.tsurugidb.belayer.webapi.dto.DumpJobResult;
import com.tsurugidb.belayer.webapi.dto.DumpLoadRequestParam;
import com.tsurugidb.belayer.webapi.dto.DumpRequestParam;
import com.tsurugidb.belayer.webapi.dto.ErrorResult;
import com.tsurugidb.belayer.webapi.dto.Job;
import com.tsurugidb.belayer.webapi.dto.JobList;
import com.tsurugidb.belayer.webapi.dto.JobResult;
import com.tsurugidb.belayer.webapi.dto.LoadRequestParam;
import com.tsurugidb.belayer.webapi.exception.BadRequestException;
import com.tsurugidb.belayer.webapi.exception.UnauthorizationException;
import com.tsurugidb.belayer.webapi.model.SystemTime;
import com.tsurugidb.belayer.webapi.security.UserTokenAuthentication;
import com.tsurugidb.belayer.webapi.service.DumpLoadService;
import com.tsurugidb.belayer.webapi.service.FileSystemService;
import com.tsurugidb.belayer.webapi.service.JobIdService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Slf4j
@Component
public class DumpLoadApiHandler {

    @Autowired
    DumpLoadService dumpLoadService;

    @Autowired
    JobIdService jobIdService;

    @Autowired
    FileSystemService fileSystemService;

    @Autowired
    LoadHelper loadHelper;

    @Autowired
    SystemTime systemTime;
  
    /**
     * Start Dump API
     *
     * @param req Request
     * @return Response
     */
    public Mono<ServerResponse> requestDump(ServerRequest req) {

        String jobId = jobIdService.createNewJobId();

        Mono<JobResult> result = ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> fillDumpParams(auth, req, jobId))
                .map(this::checkDir)
                .flatMap(dumpLoadService::startDump)
                .map(j -> this.createDumpResult((DumpJob) j));

        return ServerResponse.ok().body(
                BodyInserters.fromProducer(result, JobResult.class));
    }

    private DumpRequestParam fillDumpParams(Authentication auth, ServerRequest req, String jobId) {
        Objects.requireNonNull(auth);
        if (auth instanceof UserTokenAuthentication) {
            ((UserTokenAuthentication) auth).getTokenExpirationTime()
                    .filter((it) -> systemTime.now().isBefore(it))
                    .orElseThrow(() -> new UnauthorizationException("Token is expired.", "Token is expired."));
        }

        DumpRequestParam param = new DumpRequestParam();
        param.setUid(auth.getName());
        param.setCredentials(auth.getCredentials());

        param.setDirPath(req.pathVariable("dirpath"));
        param.setJobId(jobId);
        param.setTable(req.pathVariable("table"));
        param.setFormat(req.queryParam("format").orElse(DumpLoadRequestParam.FORMAT_PARQUET));
        param.setWaitUntilDone(Boolean.valueOf(req.queryParam("wait_until_done").orElse("false")));

        return param;
    }

    private DumpRequestParam checkDir(DumpRequestParam param) {

        // check dir path -> throw error
        fileSystemService.checkDirPath(param.getUid(), param.getDirPath());

        return param;
    }

    public Mono<ServerResponse> requestLoad(ServerRequest req) {
        String jobId = jobIdService.createNewJobId();

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> fillLoadParams(auth, req, jobId))
                .map(param -> {
                    loadHelper.checkLoadFiles(param.getUid(), param.getFiles());
                    loadHelper.checkLoadMapping(param.getMappings());
                    return param;
                })
                .flatMap(dumpLoadService::startLoad)
                .map(this::createLoadResult)
                .flatMap(result -> ServerResponse.ok().body(BodyInserters.fromValue(result)))
                .switchIfEmpty(ServerResponse.badRequest().bodyValue(new ErrorResult("no paramters in request body")));
    }

    private Mono<LoadRequestParam> fillLoadParams(Authentication auth, ServerRequest req, String jobId) {
        log.debug("auth:{}, jobId:{}", auth, jobId);

        Objects.requireNonNull(auth);

        if (auth instanceof UserTokenAuthentication) {
            ((UserTokenAuthentication) auth).getTokenExpirationTime()
                    .filter((it) -> systemTime.now().isBefore(it))
                    .orElseThrow(() -> new UnauthorizationException("Token is expired.", "Token is expired."));
        }

        return req.bodyToMono(LoadRequestParam.class)
                // fill params
                .map(param -> {
                    param.setUid(auth.getName());
                    param.setCredentials(auth.getCredentials());
                    param.setJobId(jobId);
                    param.setTable(req.pathVariable("table"));
                    param.setFormat(req.queryParam("format").orElse(DumpLoadRequestParam.FORMAT_DETECT_BY_EXTENSION));
                    boolean useSingleTransaction = Boolean.valueOf(req.queryParam("transactional").orElse("true"));
                    param.setSingleTransaction(useSingleTransaction);
                    param.setWaitUntilDone(Boolean.valueOf(req.queryParam("wait_until_done").orElse("false")));

                    return param;
                });
    }

    private DumpJobResult createDumpResult(DumpJob job) {
        log.debug("job:{}", job);
        var result = new DumpJobResult();
        result.setJobId(job.getJobId());
        result.setUid(job.getUid());
        result.setType(job.getType());
        if (job.isWaitUntilDone()) {
            result.setFiles(job.getFiles());
            result.setStatus(job.getStatus().name());
            result.setErrorMessage(job.getErrorMessage());
        }
        return result;
    }

    private JobResult createLoadResult(Job job) {
        log.debug("job:{}", job);
        var result = new DumpJobResult();
        result.setJobId(job.getJobId());
        result.setUid(job.getUid());
        result.setType(job.getType());

        return result;
    }

    /**
     * Show specified job.
     *
     * @param request Request
     * @return Response
     */
    public Mono<ServerResponse> showJobDetail(final ServerRequest request) {
        var jobId = request.pathVariable("jobid");
        var type = request.pathVariable("type");
        if (!Job.TYPE_DUMP.equals(type) && !Job.TYPE_LOAD.equals(type)) {
            var msg = "Invalid parameter. type:" + type;
            throw new BadRequestException(msg, msg);
        }

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .map(uid -> dumpLoadService.getJob(type, uid, jobId))
                .flatMap(job -> ServerResponse.ok().body(
                        BodyInserters.fromProducer(job, Job.class)));
    }

    /**
     * Show list of the jobs.
     *
     * @param request Request
     * @return Response
     */
    public Mono<ServerResponse> listJobs(final ServerRequest request) {
        var type = request.pathVariable("type");

        if (!Job.TYPE_DUMP.equals(type) && !Job.TYPE_LOAD.equals(type)) {
            var msg = "Invalid parameter. type:" + type;
            throw new BadRequestException(msg, msg);
        }

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .flatMapMany(uid -> dumpLoadService.getJobList(type, uid))
                .collectList()
                .flatMap(jobs -> ServerResponse.ok().body(
                        BodyInserters.fromProducer(Mono.just(new JobList(jobs)),
                                JobList.class)));
    }

    /**
     * Cancel specified dump/load job.
     *
     * @param request Request
     * @return Response
     */
    public Mono<ServerResponse> cancelJob(final ServerRequest request) {
        var jobId = request.pathVariable("jobid");
        var type = request.pathVariable("type");

        if (!Job.TYPE_DUMP.equals(type) && !Job.TYPE_LOAD.equals(type)) {
            var msg = "Invalid parameter. type:" + type;
            throw new BadRequestException(msg, msg);
        }

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .map(uid -> dumpLoadService.cancelJob(type, uid, jobId))
                .flatMap(job -> ServerResponse.ok().body(
                        BodyInserters.fromProducer(job, Job.class)));
    }

}
