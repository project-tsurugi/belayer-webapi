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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.tsurugidb.belayer.webapi.api.helper.LoadHelper;
import com.tsurugidb.belayer.webapi.api.helper.UploadHelper;
import com.tsurugidb.belayer.webapi.dto.ColumnMapping;
import com.tsurugidb.belayer.webapi.dto.DumpRequestParam;
import com.tsurugidb.belayer.webapi.dto.DumpResult;
import com.tsurugidb.belayer.webapi.dto.LoadParameter;
import com.tsurugidb.belayer.webapi.dto.LoadResult;
import com.tsurugidb.belayer.webapi.dto.LongTransactionJob;
import com.tsurugidb.belayer.webapi.dto.StreamDumpRequestBody;
import com.tsurugidb.belayer.webapi.dto.StreamDumpRequestParam;
import com.tsurugidb.belayer.webapi.dto.TransactionApiParameter;
import com.tsurugidb.belayer.webapi.dto.TransactionFinishType;
import com.tsurugidb.belayer.webapi.dto.TransactionMode;
import com.tsurugidb.belayer.webapi.dto.TransactionStartBody;
import com.tsurugidb.belayer.webapi.dto.TransactionStatus;
import com.tsurugidb.belayer.webapi.dto.UploadContext;
import com.tsurugidb.belayer.webapi.exception.BadRequestException;
import com.tsurugidb.belayer.webapi.exception.IORuntimeException;
import com.tsurugidb.belayer.webapi.exception.InternalServerErrorException;
import com.tsurugidb.belayer.webapi.exception.UnauthorizationException;
import com.tsurugidb.belayer.webapi.model.Constants;
import com.tsurugidb.belayer.webapi.model.SystemTime;
import com.tsurugidb.belayer.webapi.security.UserTokenAuthentication;
import com.tsurugidb.belayer.webapi.service.DumpLoadService;
import com.tsurugidb.belayer.webapi.service.FileSystemService;
import com.tsurugidb.belayer.webapi.service.JobIdService;
import com.tsurugidb.belayer.webapi.service.StatefulDumpLoadService;
import com.tsurugidb.belayer.webapi.service.TsubakuroService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Component
@Slf4j
public class StatefulApiHandler {

    @Autowired
    JobIdService jobIdService;

    @Autowired
    StatefulDumpLoadService statefulDumploadService;

    @Autowired
    DumpLoadService dumpLoadService;

    @Autowired
    private UploadHelper uploadHelper;

    @Autowired
    ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Autowired
    TsubakuroService tsubakuroService;

    @Autowired
    private FileSystemService fileSystemService;

    @Autowired
    LoadHelper loadHelper;

    @Autowired
    SystemTime systemTime;

    /**
     * Start Transaction API
     *
     * @param req Request
     * @return Response
     */
    public Mono<ServerResponse> startTransaction(ServerRequest req) {

        String jobId = jobIdService.createNewJobId();

        Mono<TransactionStatus> status = ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> this.createTransactionStartParameter(auth, req, jobId))
                .flatMap(statefulDumploadService::startTransaction)
                .map(this::createTransactionStatus);

        return ServerResponse.ok().body(
                BodyInserters.fromProducer(status, TransactionStatus.class));
    }

    /**
     * Finish Transaction API
     *
     * @param req Request
     * @return Response
     */
    public Mono<ServerResponse> finishTransaction(ServerRequest req) {

        Mono<TransactionStatus> status = ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> this.createParameter(auth, req, true))
                .flatMap(statefulDumploadService::finishTransaction)
                .map(this::createTransactionStatus);

        return ServerResponse.ok().body(
                BodyInserters.fromProducer(status, TransactionStatus.class));
    }

    /**
     * Show Transaction Status API
     *
     * @param req Request
     * @return Response
     */
    public Mono<ServerResponse> showTransactionStatus(ServerRequest req) {

        Mono<TransactionStatus> status = ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> this.createParameter(auth, req, false))
                .flatMap(statefulDumploadService::finishTransaction)
                .map(this::createTransactionStatus);

        return ServerResponse.ok().body(
                BodyInserters.fromProducer(status, TransactionStatus.class));
    }

    private Mono<TransactionApiParameter> createTransactionStartParameter(Authentication auth, ServerRequest req,
            String jobId) {

        Objects.requireNonNull(auth);
        if (auth instanceof UserTokenAuthentication) {
            ((UserTokenAuthentication) auth).getTokenExpirationTime()
                    .filter((it) -> systemTime.now().isBefore(it))
                    .orElseThrow(() -> new UnauthorizationException("Token is expired.", "Token is expired."));
        }

        return req.bodyToMono(TransactionStartBody.class)
                .switchIfEmpty(Mono.just(new TransactionStartBody()))
                .map(body -> {

                    var param = new TransactionApiParameter();
                    param.setUid(auth.getName());
                    param.setCredentials(auth.getCredentials().toString());
                    param.setTransactionId(jobId);

                    var type = body.getType();
                    var tranMode = TransactionMode.fromName(type);
                    if (tranMode == null) {
                        var msg = "Invalid parameter. Specify read_only/read_write. transaction_type:" + type;
                        throw new BadRequestException(msg, msg);
                    }
                    param.setTransactionMode(tranMode);

                    try {
                        int timeoutMin = Integer.parseInt(body.getTimeoutMin());
                        param.setTimeoutMin(timeoutMin);
                    } catch (NumberFormatException ex) {
                        var msg = "Invalid timeout value.";
                        throw new BadRequestException(msg, msg);
                    }

                    param.setTables(body.getTables());

                    return param;
                });

    }

    private TransactionApiParameter createParameter(Authentication auth, ServerRequest req, boolean finishTransaction) {

        Objects.requireNonNull(auth);
        if (auth instanceof UserTokenAuthentication) {
            ((UserTokenAuthentication) auth).getTokenExpirationTime()
                    .filter((it) -> systemTime.now().isBefore(it))
                    .orElseThrow(() -> new UnauthorizationException("Token is expired.", "Token is expired."));
        }

        var param = new TransactionApiParameter();
        param.setUid(auth.getName());
        param.setCredentials(auth.getCredentials().toString());
        param.setTransactionId(req.pathVariable("transaction_id"));

        String type = "status";
        if (finishTransaction) {
            type = req.pathVariable("type");
        }

        var finishType = TransactionFinishType.fromName(type);
        if (finishType == null) {
            var msg = "Invalid parameter. Specify commit/rollback. type:" + type;
            throw new BadRequestException(msg, msg);
        }
        param.setFinishType(finishType);

        return param;
    }

    private TransactionStatus createTransactionStatus(LongTransactionJob job) {
        var st = new TransactionStatus();
        st.setTransactionId(job.getJobId());
        st.setType(job.getTransactionMode().getTypeName());
        st.setStartTime(job.getStartTime());
        st.setEndTime(job.getEndTime());
        st.setStatus(job.getStatus().name());

        return st;
    }

    /**
     * Stream Dump API
     *
     * @param req Request
     * @return Response
     */
    public Mono<ServerResponse> getDump(ServerRequest req) {

        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(auth -> fillDumpParams(auth, req))
                .flatMap(param -> {
                    var mode = param.getMode();
                    log.debug("mode: " + mode);
                    if (mode == null || mode.equals("normal")) {
                        // return as normal response
                        return Mono.just(param)
                                .flatMapMany(dumpLoadService::getDump)
                                .collectList()
                                .flatMap(list -> this.createNormalResponse(list, req, param.getFormat()));
                    } else {
                        // return as SSE
                        return createSseRespose(Mono.just(param).flatMapMany(dumpLoadService::getDump),
                                req, param.getFormat());
                    }
                });
    }

    private Mono<StreamDumpRequestParam> fillDumpParams(Authentication auth, ServerRequest req) {

        return req.bodyToMono(StreamDumpRequestBody.class)
                .switchIfEmpty(Mono.just(new StreamDumpRequestBody()))
                .map(body -> {
                    StreamDumpRequestParam param = new StreamDumpRequestParam();
                    param.setUid(auth.getName());
                    param.setCredentials(auth.getCredentials());

                    param.setDirPath("./");
                    param.setJobId(req.pathVariable("transaction_id"));
                    param.setTable(req.pathVariable("table_name"));
                    var format = body.getFormat();
                    if (format != null && format.equals(DumpRequestParam.FORMAT_CSV)) {
                        param.setFormat(DumpRequestParam.FORMAT_CSV);
                    } else {
                        param.setFormat(DumpRequestParam.FORMAT_PARQUET);
                    }
                    param.setMode(body.getMode());

                    return param;
                });
    }

    private Mono<ServerResponse> createNormalResponse(List<String> list, ServerRequest req, String format) {
        var result = new DumpResult();
        result.setDownloadPathList(list);
        result.setTable(req.pathVariable("table_name"));
        result.setTransactionId(req.pathVariable("transaction_id"));
        result.setFormat(format);

        return ServerResponse.ok().body(BodyInserters.fromValue(result));
    }

    private Mono<ServerResponse> createSseRespose(Flux<String> downloadPathList, ServerRequest req, String format) {
        Flux<String> params = Flux.just("table_name=" + req.pathVariable("table_name"),
                "transaction_id=" + req.pathVariable("transaction_id"),
                "format=" + format);
        Flux<String> dlPath = downloadPathList
                .map(downloadPath -> "download_path=" + downloadPath.replace("/", "%2F"));
        Flux<ServerSentEvent<String>> eventsPublisher = Flux.concat(params, dlPath)
                .map(msg -> ServerSentEvent.builder(msg).build());
        return ServerResponse
                .ok().cacheControl(CacheControl.noCache())
                .body(BodyInserters.fromServerSentEvents(eventsPublisher));
    }

    /**
     * Stream Load API
     *
     * @param req Request
     * @return Response
     */
    public Mono<ServerResponse> loadDumpFiles(ServerRequest req) {
        String transactionId = req.pathVariable("transaction_id");

        var param = new LoadParameter();
        // use transactionId as directry name
        param.setDestDirPath(transactionId);
        param.setJobId(transactionId);
        param.setTable(req.pathVariable("table_name"));

        uploadHelper.checkRequestHeader(req);

        Mono<LoadResult> result = ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(auth -> {
                    param.setUid(auth.getName());
                    param.setCredentials(auth.getCredentials());
                    return param;
                })
                .flatMap(p -> fillLoadParam(req, p))
                .map(p -> {
                    loadHelper.checkLoadMapping(p.getColMap());
                    fileSystemService.createDirectory(p.getUid(), p.getDestDirPath());
                    createTempDirectory(p);
                    UploadContext context = createUploadContext(p);
                    log.debug("context:" + context);
                    return context;
                })
                .map(ctx -> dumpLoadService.useTransaction(ctx))
                .flatMapMany(ctx -> {
                    log.debug("flux:{}", ctx.getUploadFilePathFlux());
                    return ctx.getUploadFilePathFlux();
                })
                .parallel()
                .runOn(Schedulers.fromExecutor(threadPoolTaskExecutor))
                .flatMap(dumpFilePath -> {
                    log.debug("upload path:" + dumpFilePath);
                    return dumpLoadService.loadDumpFile(param, Path.of(dumpFilePath));
                })
                .map(filePath -> {
                    fileSystemService.deleteFile(param.getUid(), filePath);
                    return filePath;
                })
                .collectSortedList(Comparator.naturalOrder())
                .map(downloadFilePathList -> setLoadResult(param, downloadFilePathList))
                .map(res -> {
                    if (param.getTempDir() != null) {
                        fileSystemService.deleteDirectoryWithContent(param.getTempDir());
                    }
                    return res;
                })
                .onErrorResume(th -> {
                    dumpLoadService.registerFailedResult(param, th);
                    throw new InternalServerErrorException(th.getMessage(), th);
                });

        return ServerResponse.ok().body(
                BodyInserters.fromProducer(result, LoadResult.class));
    }

    private Mono<LoadParameter> fillLoadParam(ServerRequest req, LoadParameter param) {

        return req.body(BodyExtractors.toParts())
                .map(part -> {
                    log.debug("part:" + part);
                    if (part.name().equals("file") && part instanceof FilePart) {
                        param.addFilePart((FilePart) part);
                        log.debug("add file:" + part);
                    }

                    if (part.name().equals("col-map") && part instanceof FormFieldPart) {
                        String value = ((FormFieldPart) part).value();
                        log.debug("add col-map:" + value);
                        ColumnMapping colMapping = toColMapping(value);
                        if (colMapping != null) {
                            param.addColMapping(colMapping);
                        }
                    }
                    if (part.name().equals("format") && part instanceof FormFieldPart) {
                        String value = ((FormFieldPart) part).value();
                        param.setFormat(value);
                    }
                    return "dummy";
                })
                .collectList()
                .then(Mono.just(param));
    }

    private ColumnMapping toColMapping(String value) {
        var items = value.split(",");
        if (items.length < 2) {
            return null;
        }
        ColumnMapping mapping = new ColumnMapping();
        mapping.setSourceColumn(items[0]);
        mapping.setTargetColumn(items[1]);
        return mapping;
    }

    private void createTempDirectory(LoadParameter param) {
        try {
            Path tmpDir = Files.createTempDirectory(Constants.TEMP_DIR_PREFIX_DUMP + param.getJobId());
            param.setTempDir(tmpDir);
        } catch (IOException ex) {
            throw new IORuntimeException("can't create temp directory.", ex);
        }
    }

    private UploadContext createUploadContext(LoadParameter param) {

        Flux<String> pathFlux = Mono.just(param)
                .flatMapMany(p -> Flux.fromIterable(param.getFileParts()))
                .flatMap(filePart -> {
                    log.debug("fp:" + filePart);
                    Path absolutePath = fileSystemService.convertToAbsolutePath(param.getUid(), param.getDestDirPath());
                    Mono<String> path = uploadHelper.saveFile(param.getUid(), absolutePath, filePart);
                    return path;
                });

        var ctx = new UploadContext();
        ctx.setParam(param);
        ctx.setUploadFilePathFlux(pathFlux);

        return ctx;
    }

    private LoadResult setLoadResult(LoadParameter param, List<String> downloadFilePathList) {

        dumpLoadService.finishDumpOrLoad(param.getUid(), param.getJobId());

        var result = new LoadResult();
        result.setTransactionId(param.getJobId());
        result.setTable(param.getTable());
        result.setDumpFiles(downloadFilePathList);
        result.setFormat(param.getFormat());

        return result;
    }

}
