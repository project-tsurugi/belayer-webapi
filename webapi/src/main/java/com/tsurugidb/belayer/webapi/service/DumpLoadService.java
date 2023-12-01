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

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import com.tsurugidb.belayer.webapi.dto.DumpJob;
import com.tsurugidb.belayer.webapi.dto.DumpLoadRequestParam;
import com.tsurugidb.belayer.webapi.dto.DumpRequestParam;
import com.tsurugidb.belayer.webapi.dto.Job;
import com.tsurugidb.belayer.webapi.dto.Job.JobStatus;
import com.tsurugidb.belayer.webapi.dto.LoadFileInfo;
import com.tsurugidb.belayer.webapi.dto.LoadJob;
import com.tsurugidb.belayer.webapi.dto.LoadParameter;
import com.tsurugidb.belayer.webapi.dto.LoadRequestParam;
import com.tsurugidb.belayer.webapi.dto.LongTransactionJob;
import com.tsurugidb.belayer.webapi.dto.TransactionalJob;
import com.tsurugidb.belayer.webapi.dto.UploadContext;
import com.tsurugidb.belayer.webapi.exception.BadRequestException;
import com.tsurugidb.belayer.webapi.exception.IORuntimeException;
import com.tsurugidb.belayer.webapi.exception.InternalServerErrorException;
import com.tsurugidb.belayer.webapi.exception.NoDataException;
import com.tsurugidb.belayer.webapi.exception.NotFoundException;
import com.tsurugidb.belayer.webapi.model.Constants;
import com.tsurugidb.belayer.webapi.model.JobManager;
import com.tsurugidb.belayer.webapi.model.ZipFileUtil;
import com.tsurugidb.belayer.webapi.util.FileUtil;

import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Slf4j
@Component
public class DumpLoadService {

    @Autowired
    JobManager jobManager;

    @Autowired
    ThreadPoolTaskExecutor threadPoolTaskExecutor;

    @Autowired
    TsubakuroService tsubakuroService;

    @Autowired
    FileSystemService fileSystemService;

    @Autowired
    ParquetService parquetService;

    @Value("${webapi.load.progress_percentage_filesize_sum_computed}")
    private int loadProgressPercentageWhenSumComputed;

    /**
     * Execute dump.
     *
     * @param param dump request parameter
     * @return Job
     */
    public Mono<TransactionalJob> startDump(DumpRequestParam param) {

        log.debug("register job :" + param.toString());

        var job = createDumpJob(param);
        jobManager.registerJob(job);

        log.debug("dump start :" + job.toString());

        Mono<TransactionalJob> resultJob = Mono.just(job)
                .map(tsubakuroService::createDumpTransaction)
                .flatMapMany(tsubakuroService::dumpTable)
                .parallel()
                .runOn(Schedulers.fromExecutor(threadPoolTaskExecutor))
                .map(filePath -> {
                    // set progress
                    job.addProgressNumerator(FileUtil.getFileSize(filePath));
                    return filePath;
                })
                // convert format to CSV if specified
                .map(filePath -> this.convertParquetToCsvIfNecessary(filePath, param.getFormat(), param.getUid(),
                        param.getDirPath() + "/" + param.getJobId()))
                .collectSortedList(Comparator.naturalOrder())
                .flatMap(result -> setDumpResult(param.getUid(), param.getJobId(), result))
                .flatMap(this::registerCompletedResult)
                .flatMap(j -> deleteTempDir(j))
                .onErrorResume(ex -> registerFailedResult(job, ex));

        if (param.isWaitUntilDone()) {
            return resultJob;
        }

        Disposable disp = resultJob
                .subscribeOn(Schedulers.fromExecutor(threadPoolTaskExecutor))
                .subscribe();

        log.debug("set disposable:" + param.toString());
        job.setDisposable(disp);

        return Mono.just(job);
    }

    private DumpJob createDumpJob(DumpRequestParam param) {

        var job = new DumpJob();
        job.setType(Job.TYPE_DUMP);
        job.setUid(param.getUid());
        job.setCredentials(param.getCredentials());
        job.setJobId(param.getJobId());
        job.setStatus(JobStatus.RUNNING);
        job.setTable(param.getTable());
        job.setDirPath(param.getDirPath());
        job.setFormat(param.getFormat());
        job.setWaitUntilDone(param.isWaitUntilDone());

        // Parquet: specify destination directory as output directory with Tsubakuro
        // API.
        // CSV: specify temp directory as output directry for Tsubakuro API and then
        // convert to CSV in the disttnation directory.
        boolean outputTempDir = !param.getFormat().equals(DumpLoadRequestParam.FORMAT_PARQUET);
        job.setOutputTempDir(outputTempDir);

        return job;
    }

    private Mono<TransactionalJob> deleteTempDir(TransactionalJob job) {
        if (job.getTempDir() != null) {
            fileSystemService.deleteDirectoryWithContent(job.getTempDir());
        }

        return Mono.just(job);
    }

    /**
     * Obtain dump files.
     *
     * @param param dump request parameter
     * @return Flex of parquet's download path
     */
    public Flux<String> getDump(DumpRequestParam param) {

        log.debug("get job :" + param.toString());
        Optional<Job> targetJob = jobManager.getJob(Job.TYPE_TRANSACTION, param.getUid(), param.getJobId());
        if (targetJob.isEmpty()) {
            var msg = "Specified transaction is not found. transactionId:" + param.getJobId();
            throw new NotFoundException(msg, msg, null);
        }

        var job = (LongTransactionJob) targetJob.get();
        synchronized (job) {
            useTransaction(job);
        }

        log.debug("dump start :" + job.toString());

        Flux<String> result = Mono.just(job)
                .map(j -> this.convertToDumpJob(j, param))
                .flatMapMany(tsubakuroService::dumpTable)
                .parallel()
                .runOn(Schedulers.fromExecutor(threadPoolTaskExecutor))
                .sequential()
                .map(filePath -> this.convertParquetToCsvIfNecessary(filePath, param.getFormat(), param.getUid(),
                        param.getDirPath() + param.getJobId()))
                .doOnComplete(() -> this.finishDumpOrLoad(job.getUid(), job.getJobId()))
                .doOnComplete(() -> this.deleteTempDir(job))
                .onErrorResume(th -> {
                    if (th instanceof NoDataException) {
                        return Flux.empty();
                    }
                    this.registerFailedResult(job, th);
                    throw new InternalServerErrorException(th.getMessage(), th);
                });

        return result;
    }

    private DumpJob convertToDumpJob(LongTransactionJob tranJob, DumpRequestParam param) {

        var job = new DumpJob();
        job.setTsurugiTransaction(tranJob.getTsurugiTransaction());
        job.setType(Job.TYPE_DUMP);
        job.setUid(tranJob.getUid());
        job.setCredentials(tranJob.getCredentials());
        job.setJobId(tranJob.getJobId());
        job.setTable(param.getTable());
        job.setDirPath(param.getDirPath());
        job.setFormat(param.getFormat());

        boolean outputTempDir = !param.getFormat().equals(DumpLoadRequestParam.FORMAT_PARQUET);
        job.setOutputTempDir(outputTempDir);
        return job;
    }

    private String convertParquetToCsvIfNecessary(Path inFilePath, String format, String uid, String dirPath) {

        if (format.equals(DumpLoadRequestParam.FORMAT_PARQUET)) {
            return fileSystemService.convertToDownloadPath(uid, inFilePath.toString()).toString();
        }

        Path destPath = fileSystemService.convertToAbsolutePath(uid, dirPath);

        String fileName = inFilePath.getFileName().toString();
        int index = fileName.lastIndexOf(".");
        String csvFileName = fileName.substring(0, index) + ".csv";
        Path csvOutPath = Path.of(destPath.toString(), csvFileName);

        parquetService.convertParquetToCsv(inFilePath.toString(), csvOutPath.toString());

        return fileSystemService.convertToDownloadPath(uid, csvOutPath.toString()).toString();
    }

    /**
     * Execute load.
     *
     * @param param load request parameter
     * @return Job
     */
    public Mono<TransactionalJob> startLoad(LoadRequestParam param) {

        log.debug("register job :" + param.toString());

        var job = createLoadJob(param);
        jobManager.registerJob(job);

        log.debug("load start :" + job.toString());

        Mono<TransactionalJob> resultJob = Mono.just(job)
                .map(tsubakuroService::createLoadTransaction)
                .map(j -> this.createTempDir(job))
                .map(j -> this.expandZipIfNecessary(job))
                .flatMapMany(this::getLoadTargetFileAbsolutePath)
                .parallel()
                .runOn(Schedulers.fromExecutor(threadPoolTaskExecutor))
                .flatMap(loadFileInfo -> tsubakuroService.loadFile(job, loadFileInfo))
                .collectSortedList(Comparator.naturalOrder())
                .flatMap(result -> setLoadResult(param.getUid(), param.getJobId(), job.getFiles()))
                .flatMap(this::registerCompletedResult)
                .flatMap(j -> deleteTempDir(j))
                .onErrorResume(ex -> registerFailedResult(job, ex));

        if (param.isWaitUntilDone()) {
            return resultJob;
        }

        Disposable disp = resultJob
                .subscribeOn(Schedulers.fromExecutor(threadPoolTaskExecutor))
                .subscribe();

        log.debug("set disposable:" + param.toString());
        job.setDisposable(disp);

        return Mono.just(job);
    }

    private LoadJob createLoadJob(LoadRequestParam param) {

        var job = new LoadJob();
        job.setType(Job.TYPE_LOAD);
        job.setUid(param.getUid());
        job.setCredentials(param.getCredentials());
        job.setTable(param.getTable());
        job.setJobId(param.getJobId());
        job.setFiles(param.getFiles());
        job.setFormat(param.getFormat());
        job.setTransactionNeeded(param.isTransactional());
        job.setMappings(param.getMappings());
        job.setStatus(JobStatus.RUNNING);

        return job;
    }

    private LoadJob createTempDir(LoadJob job) {
        Path tmpDir = fileSystemService.createTempDirectory(Constants.TEMP_DIR_PREFIX_LAOD + job.getJobId());
        log.debug("temp dir:" + tmpDir);
        job.setTempDir(tmpDir);

        return job;
    }

    private LoadJob expandZipIfNecessary(LoadJob job) {
        try {
            var format = job.getFormat();
            var files = job.getFiles();
            for (String inFilePath : files) {
                if (format.equals(DumpLoadRequestParam.FORMAT_ZIP) ||
                        (format.equals(DumpLoadRequestParam.FORMAT_DETECT_BY_EXTENSION)
                                && inFilePath.toString().endsWith(Constants.EXT_ZIP))) {
                    log.debug("expandZip:{}", inFilePath);
                    // expand zip for tmp dir
                    Path workDir = fileSystemService
                            .createTempDirectory(Constants.TEMP_DIR_PREFIX_LAOD_ZIP + job.getJobId());
                    Path zipFilePath = fileSystemService.convertToAbsolutePath(job.getUid(), inFilePath);
                    ZipFileUtil.extractZipFile(workDir, zipFilePath);

                    // add file path from zip contents
                    var fileList = fileSystemService.getFileList(workDir, false, true);
                    job.setFilesFromZip(fileList);
                }
            }
            return job;
        } catch (IOException ex) {
            throw new IORuntimeException("Error while treating a zip file.", ex);
        }
    }

    private Flux<LoadFileInfo> getLoadTargetFileAbsolutePath(LoadJob job) {
        log.debug("job:{}", job);
        var list = new ArrayList<Path>();
        for (String filePath : job.getFiles()) {
            if (!filePath.endsWith(Constants.EXT_ZIP)) {
                list.add(fileSystemService.convertToAbsolutePath(job.getUid(), filePath));
            }
        }
        if (job.getFilesFromZip() != null) {
            for (String filePath : job.getFilesFromZip()) {
                list.add(Path.of(filePath));
            }
        }

        log.debug("pathList:{}", list);
        if (list.size() == 0) {
            throw new IllegalArgumentException("no files to load.");
        }

        // calcurate filesize sum
        long fileSizeSum = 0;
        var parquetPathList = new ArrayList<LoadFileInfo>();
        for (var path : list) {
            LoadFileInfo info = convertCsvToParquetIfNecessary(path, job.getUid(), job.getFormat(),job.getTempDir());
            var parquetPath= info.getFilePath();
            parquetPathList.add(info);
            long fileSize = FileUtil.getFileSize(parquetPath);
            log.debug("file size:" + fileSize);

            fileSizeSum += fileSize;
        }
        // save progress
        int rate = loadProgressPercentageWhenSumComputed;

        log.debug("file size sum:" + fileSizeSum);
        long denominator = BigDecimal.valueOf(fileSizeSum)
            .divide(BigDecimal.valueOf(100 - rate).divide(BigDecimal.valueOf(100)), 2, RoundingMode.UP).longValue();
        long numerator = BigDecimal.valueOf(denominator).multiply(BigDecimal.valueOf(rate))
            .divide(BigDecimal.valueOf(100), new MathContext(0, RoundingMode.DOWN)).longValue();
        log.debug("num/dnm={}/{}", numerator, denominator);

        job.setProgressDenominator(denominator);
        job.addProgressNumerator(numerator);

        return Flux.fromIterable(parquetPathList);
    }

    private LoadFileInfo convertCsvToParquetIfNecessary(Path inFilePath, String uid, String format, Path tmpDir) {

        log.debug("convertC2P() inFilePath:{},uid:{},format:{},tmpDir:{}", inFilePath, uid, format, tmpDir);

        if (format.equals(DumpLoadRequestParam.FORMAT_PARQUET) ||
                format.equals(DumpLoadRequestParam.FORMAT_ZIP) ||
                (format.equals(DumpLoadRequestParam.FORMAT_DETECT_BY_EXTENSION)
                        && !inFilePath.toString().endsWith(Constants.EXT_CSV))) {
            log.debug("load parquet:" + inFilePath);
            return new LoadFileInfo(inFilePath, inFilePath, false);
        }

        String fileName = inFilePath.getFileName().toString();

        int index = fileName.lastIndexOf(".");
        String parquetFileName = fileName.substring(0, index) + "_csv" + Constants.EXT_PARQUET;
        var parquetFilePath = Path.of(tmpDir.toString(), parquetFileName);

        parquetService.convertCsvToParquet(inFilePath.toString(), parquetFilePath.toString());

        log.debug("load parquet converted from CSV:" + parquetFilePath);
        return new LoadFileInfo(parquetFilePath, inFilePath, true);
    }

    public UploadContext useTransaction(UploadContext ctx) {
        var param = (LoadParameter) ctx.getParam();
        var job = getTransactionJob(param);
        log.debug("load job start :" + job.toString());
        synchronized (job) {
            useTransaction(job);
        }
        job.setLoadFiles(ctx.getUploadFilePathFlux());
        param.setTransactionJob(job);

        return ctx;
    }

    public LongTransactionJob getTransactionJob(LoadParameter param) {
        log.debug("get job :" + param.toString());
        Optional<Job> targetJob = jobManager.getJob(Job.TYPE_TRANSACTION, param.getUid(), param.getJobId());
        if (targetJob.isEmpty()) {
            var msg = "Specified transaction is not found. transactionId:" + param.getJobId();
            throw new BadRequestException(msg, msg);
        }

        var job = (LongTransactionJob) targetJob.get();
        return job;
    }

    /**
     * load the single dump file.
     *
     * @param param        Load paremeter.
     * @param dumpFilePath absolute full path of dump file.
     * @return path of the uploaded file.
     */
    public Mono<String> loadDumpFile(LoadParameter param, Path dumpFilePath) {
        var job = param.getTransactionJob();
        log.debug("load dump file start :{}[{}]", param.getJobId(), dumpFilePath);

        LoadFileInfo loadFileInfo = this.convertCsvToParquetIfNecessary(dumpFilePath, param.getUid(),
                param.getFormat(), param.getTempDir());

        Mono<String> downloadPath = Mono.just(job)
                .map(j -> this.convertToLoadJob(j, param))
                .flatMap(j -> tsubakuroService.loadFile(j, loadFileInfo))
                .map(parquetFilePath -> fileSystemService.convertToDownloadPath(param.getUid(), dumpFilePath.toString())
                        .toString());

        return downloadPath;

    }

    private LoadJob convertToLoadJob(LongTransactionJob tranJob, LoadParameter param) {
        var job = new LoadJob();
        job.setTsurugiTransaction(tranJob.getTsurugiTransaction());
        job.setType(Job.TYPE_LOAD);
        job.setUid(tranJob.getUid());
        job.setCredentials(tranJob.getCredentials());
        job.setJobId(tranJob.getJobId());
        job.setTable(param.getTable());
        job.setFormat(param.getFormat());
        return job;
    }

    private void useTransaction(LongTransactionJob job) {
        if (job.getStatus() != JobStatus.AVAILABLE && job.getStatus() != JobStatus.IN_USE) {
            var msg = String.format("Specified transction in not for use. [transactionId:%s, status:%s]",
                    job.getJobId(), job.getStatus());
            throw new BadRequestException(msg, msg);
        }
        job.setStatus(JobStatus.IN_USE);
        job.getUseCount().incrementAndGet();
    }

    public void finishDumpOrLoad(String uid, String jobId) {
        Optional<Job> targetJob = jobManager.getJob(Job.TYPE_TRANSACTION, uid, jobId);
        if (targetJob.isEmpty()) {
            var msg = "Invalid transctionId. transactionId:" + jobId;
            throw new BadRequestException(msg, msg);
        }

        var job = (LongTransactionJob) targetJob.get();
        synchronized (job) {
            int count = job.getUseCount().decrementAndGet();
            if (job.getStatus() == JobStatus.IN_USE && count == 0) {
                job.setStatus(JobStatus.AVAILABLE);
            }
            log.debug("finish dump or load:" + job.toString());
        }
    }

    /**
     * Return job.
     *
     * @param jobId Job ID
     * @return Job
     */
    public Mono<Job> getJob(String type, String uid, String jobId) {
        log.debug("jobId:" + uid + "-" + jobId);

        if (!(Job.TYPE_LOAD.equals(type) || Job.TYPE_DUMP.equals(type))) {
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
     * @param type backup or restore
     * @param uid  User ID
     * @return List of Jobs
     */
    public Flux<Job> getJobList(String type, String uid) {

        if (!(Job.TYPE_LOAD.equals(type) || Job.TYPE_DUMP.equals(type))) {
            throw new BadRequestException("Invalid type. type:" + type,
                    "Invalid type. type:" + type, null);
        }

        List<Job> jobList = jobManager.getAllJobs();

        return Flux.fromIterable(jobList)
                .filter(job -> job.getUid().equals(uid))
                .filter(job -> job.getType().equals(type));
    }

    private Mono<TransactionalJob> setDumpResult(String uid, String jobId, List<String> savedFile) {
        return getJob(Job.TYPE_DUMP, uid, jobId)
                .map(j -> {
                    var job = (DumpJob) j;
                    job.setFiles(savedFile);
                    return job;
                });
    }

    private Mono<LoadJob> setLoadResult(String uid, String jobId, List<String> savedFile) {
        var jobOp = jobManager.getJob(Job.TYPE_LOAD, uid, jobId);
        if (jobOp.isEmpty()) {
            // very rare case
            throw new NotFoundException("Specified job is not found. jobId:" + jobId,
                    "Specified job is not found. jobId:" + jobId, null);
        }

        log.debug("setLoadResult() jobId:{}", jobId);
        var targetJob = (LoadJob) jobOp.get();
        targetJob.setFiles(savedFile);
        return Mono.just(targetJob);
    }

    private Mono<TransactionalJob> registerCompletedResult(TransactionalJob job) {
        log.debug("commitTx:" + job.toString());
        tsubakuroService.commitTx(job);
        jobManager.updateJobStatus(job, JobStatus.COMPLETED, null);
        return Mono.just(job);
    }

    public Mono<TransactionalJob> registerFailedResult(LoadParameter param, Throwable th) {
        var job = getTransactionJob(param);
        return registerFailedResult(job, th);
    }

    private Mono<TransactionalJob> registerFailedResult(TransactionalJob job, Throwable th) {

        log.error("error occured then try to rollback jobId:" + job.getJobId(), th);
        tsubakuroService.rollbackTx(job);
        jobManager.updateJobStatus(job, JobStatus.FAILED, th);

        return Mono.just(job);
    }

    /**
     * Cancel Dump/Laod job.
     *
     * @param jobId
     * @return Task status
     */
    public Mono<TransactionalJob> cancelJob(String type, String uid, String jobId) {
        log.debug("cancel jobId:" + uid + "-" + jobId);

        if (!(Job.TYPE_LOAD.equals(type) || Job.TYPE_DUMP.equals(type))) {
            throw new BadRequestException("Invalid type. type:" + type,
                    "Invalid type. type:" + type, null);
        }

        var job = (TransactionalJob) jobManager.cancelJob(type, uid, jobId);

        return Mono.justOrEmpty(job);
    }

}
