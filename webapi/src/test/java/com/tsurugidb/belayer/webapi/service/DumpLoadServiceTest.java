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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.tsurugidb.belayer.webapi.dto.ColumnMapping;
import com.tsurugidb.belayer.webapi.dto.DumpJob;
import com.tsurugidb.belayer.webapi.dto.DumpLoadRequestParam;
import com.tsurugidb.belayer.webapi.dto.DumpRequestParam;
import com.tsurugidb.belayer.webapi.dto.Job;
import com.tsurugidb.belayer.webapi.dto.Job.JobStatus;
import com.tsurugidb.belayer.webapi.dto.LoadJob;
import com.tsurugidb.belayer.webapi.dto.LoadRequestParam;
import com.tsurugidb.belayer.webapi.dto.TransactionalJob;
import com.tsurugidb.belayer.webapi.exception.NotFoundException;
import com.tsurugidb.belayer.webapi.model.SystemTime;
import com.tsurugidb.belayer.webapi.util.FileUtil;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = {
        "webapi.dump.progress_percentage_api_return=0",
        "webapi.dump.progress_percentage_filesize_sum_computed=0",
        "webapi.load.progress_percentage_filesize_sum_computed=0" })
public class DumpLoadServiceTest {

    @Autowired
    DumpLoadService dumpLoadService;

    @MockBean
    TsubakuroService tsubakuroService;

    @MockBean
    SystemTime systemTime;

    @MockBean
    FileSystemService fileSystemService;

    @MockBean
    ParquetService parquetService;

    @Test
    public void test_startDump_parquet() throws Exception {

        var jobId = "jobId";
        var uid = "test_user";
        var dirPath = "dump1";
        var table = "FOO_TBL";

        var now = Instant.parse("2022-06-30T12:00:00.000Z");
        when(systemTime.now()).thenReturn(now);

        var expectJob = new DumpJob();
        expectJob.setType(Job.TYPE_DUMP);
        expectJob.setUid(uid);
        expectJob.setDirPath(dirPath);
        expectJob.setTable(table);
        expectJob.setJobId(jobId);
        expectJob.setStatus(JobStatus.COMPLETED);
        expectJob.setStartTime(now);
        expectJob.setEndTime(now);
        expectJob.setFormat(DumpLoadRequestParam.FORMAT_PARQUET);
        expectJob.setFiles(Arrays.asList(new String[] { "dump1/file1.txt" }));
        Path path = Path.of("./src/test/files/parquet/test.parquet");
        expectJob.setProgressNumerator(FileUtil.getFileSize(path));
        expectJob.setProgress(100);

        var pathList = new ArrayList<Path>();
        pathList.add(path);
        when(tsubakuroService.createDumpTransaction(any())).thenReturn(expectJob);
        when(tsubakuroService.dumpTable(any())).thenReturn(Flux.fromIterable(pathList));

        when(fileSystemService.convertToDownloadPath(anyString(), anyString()))
                .thenReturn(Path.of(dirPath + "/file1.txt"));

        var param = new DumpRequestParam();
        param.setJobId(jobId);
        param.setUid(uid);
        param.setDirPath(dirPath);
        param.setTable(table);
        param.setFormat(DumpLoadRequestParam.FORMAT_PARQUET);

        Mono<TransactionalJob> actualResult = dumpLoadService.startDump(param);

        Thread.sleep(1 * 1000L);
        var actualJob = actualResult.block();

        assertEquals(expectJob, actualJob);

        var fromJobManager = dumpLoadService.getJob(Job.TYPE_DUMP, uid, jobId);
        assertEquals(expectJob, fromJobManager.block());
    }

    @Test
    public void test_startDump_csv() throws Exception {

        var jobId = "jobId";
        var uid = "test_user";
        var dirPath = "dump1";
        var table = "FOO_TBL";

        var now = Instant.parse("2022-06-30T12:00:00.000Z");
        when(systemTime.now()).thenReturn(now);

        var expectJob = new DumpJob();
        expectJob.setType(Job.TYPE_DUMP);
        expectJob.setUid(uid);
        expectJob.setDirPath(dirPath);
        expectJob.setTable(table);
        expectJob.setJobId(jobId);
        expectJob.setStatus(JobStatus.COMPLETED);
        expectJob.setStartTime(now);
        expectJob.setEndTime(now);
        expectJob.setFormat(DumpLoadRequestParam.FORMAT_CSV);
        expectJob.setFiles(Arrays.asList(new String[] { "dump1/file1.txt" }));
        expectJob.setOutputTempDir(true);
        Path path = Path.of("./src/test/files/parquet/test.parquet");
        expectJob.setProgressNumerator(FileUtil.getFileSize(path));
        expectJob.setProgress(100);

        var pathList = new ArrayList<Path>();
        pathList.add(path);
        when(tsubakuroService.createDumpTransaction(any())).thenReturn(expectJob);
        when(tsubakuroService.dumpTable(any())).thenReturn(Flux.fromIterable(pathList));

        when(fileSystemService.convertToAbsolutePath(anyString(), anyString()))
                .thenReturn(Path.of(dirPath + "/file1.txt"));
        when(fileSystemService.convertToDownloadPath(anyString(), anyString()))
                .thenReturn(Path.of(dirPath + "/file1.txt"));

        var param = new DumpRequestParam();
        param.setJobId(jobId);
        param.setUid(uid);
        param.setDirPath(dirPath);
        param.setTable(table);
        param.setFormat(DumpLoadRequestParam.FORMAT_CSV);

        Mono<TransactionalJob> actualResult = dumpLoadService.startDump(param);

        Thread.sleep(1 * 1000L);
        var actualJob = actualResult.block();

        assertEquals(expectJob, actualJob);

        var fromJobManager = dumpLoadService.getJob(Job.TYPE_DUMP, uid, jobId);
        assertEquals(expectJob, fromJobManager.block());
    }

    @Test
    public void test_startDump_fail() throws Exception {

        var jobId = "jobId";
        var uid = "test_user";
        var dirPath = "dump1";
        var table = "FOO_TBL";
        var errorMessage = "dummy error";

        var now = Instant.parse("2022-06-30T12:00:00.000Z");
        when(systemTime.now()).thenReturn(now);

        var expectJob = new DumpJob();
        expectJob.setType(Job.TYPE_DUMP);
        expectJob.setUid(uid);
        expectJob.setDirPath(dirPath);
        expectJob.setTable(table);
        expectJob.setJobId(jobId);
        expectJob.setStatus(JobStatus.FAILED);
        expectJob.setStartTime(now);
        expectJob.setEndTime(now);
        expectJob.setErrorMessage(errorMessage);
        expectJob.setFormat(DumpLoadRequestParam.FORMAT_PARQUET);
        expectJob.setProgress(100);

        var pathList = new ArrayList<Path>();
        pathList.add(Path.of("./src/test/files/backup_restore/file1.txt"));
        when(tsubakuroService.createDumpTransaction(any())).thenThrow(new RuntimeException(errorMessage));
        when(tsubakuroService.dumpTable(any())).thenReturn(Flux.fromIterable(pathList));

        var param = new DumpRequestParam();
        param.setJobId(jobId);
        param.setUid(uid);
        param.setDirPath(dirPath);
        param.setTable(table);
        param.setFormat(DumpLoadRequestParam.FORMAT_PARQUET);

        Mono<TransactionalJob> actualResult = dumpLoadService.startDump(param);

        Thread.sleep(1 * 1000L);
        var actualJob = actualResult.block();

        assertEquals(expectJob, actualJob);

        var fromJobManager = dumpLoadService.getJob(Job.TYPE_DUMP, uid, jobId);
        assertEquals(expectJob, fromJobManager.block());
    }

    @Test
    public void test_startLoad_parquet() throws Exception {

        var jobId = "jobId";
        var uid = "test_user";
        var table = "FOO_TBL";
        String filePath = "dump1/file1.txt";

        var now = Instant.parse("2022-06-30T12:00:00.000Z");
        when(systemTime.now()).thenReturn(now);

        var expectJob = new LoadJob();
        expectJob.setType(Job.TYPE_LOAD);
        expectJob.setUid(uid);
        expectJob.setTable(table);
        expectJob.setJobId(jobId);
        expectJob.setStatus(JobStatus.COMPLETED);
        expectJob.setStartTime(now);
        expectJob.setEndTime(now);
        expectJob.setFormat(DumpLoadRequestParam.FORMAT_PARQUET);
        expectJob.setFiles(Arrays.asList(new String[] { filePath }));
        expectJob.setMappings(Arrays.asList(new ColumnMapping[] {}));
        expectJob.setProgress(100);
        Path path = Path.of("./src/test/files/parquet/test.parquet");
        expectJob.setProgressNumerator(0);
        expectJob.setProgressDenominator(FileUtil.getFileSize(path));

        var pathList = new ArrayList<String>();
        pathList.add(filePath);
        when(tsubakuroService.createLoadTransaction(any())).thenReturn(expectJob);
        when(tsubakuroService.loadFile(any(), any())).thenReturn(Mono.just(filePath));
        when(tsubakuroService.commitTx(any())).thenReturn(expectJob);

        when(fileSystemService.convertToDownloadPath(anyString(), anyString())).thenReturn(Path.of("/file1.txt"));
        when(fileSystemService.convertToAbsolutePath(anyString(), anyString())).thenReturn(path);

        var param = new LoadRequestParam();
        param.setJobId(jobId);
        param.setUid(uid);
        param.setTable(table);
        param.setFormat(DumpLoadRequestParam.FORMAT_PARQUET);
        param.setFiles(pathList);

        Mono<TransactionalJob> actualResult = dumpLoadService.startLoad(param);

        Thread.sleep(1 * 1000L);
        var actualJob = actualResult.block();
        assertEquals(expectJob, actualJob);

        var fromJobManager = dumpLoadService.getJob(Job.TYPE_LOAD, uid, jobId);
        assertEquals(expectJob, fromJobManager.block());
    }

    @Test
    public void test_startLoad_csv() throws Exception {

        var jobId = "jobId";
        var uid = "test_user";
        var table = "FOO_TBL";
        String filePath = "dump1/file1.csv";

        var now = Instant.parse("2022-06-30T12:00:00.000Z");
        when(systemTime.now()).thenReturn(now);

        var expectJob = new LoadJob();
        expectJob.setType(Job.TYPE_LOAD);
        expectJob.setUid(uid);
        expectJob.setTable(table);
        expectJob.setJobId(jobId);
        expectJob.setStatus(JobStatus.COMPLETED);
        expectJob.setStartTime(now);
        expectJob.setEndTime(now);
        expectJob.setFormat(DumpLoadRequestParam.FORMAT_DETECT_BY_EXTENSION);
        expectJob.setFiles(Arrays.asList(new String[] { filePath }));
        expectJob.setMappings(Arrays.asList(new ColumnMapping[] {}));
        expectJob.setProgress(100);
        Path path = Path.of("./src/test/files/parquet/test.parquet");
        expectJob.setProgressNumerator(0);
        expectJob.setProgressDenominator(FileUtil.getFileSize(path));

        var pathList = new ArrayList<String>();
        pathList.add(filePath);
        when(tsubakuroService.createLoadTransaction(any())).thenReturn(expectJob);
        when(tsubakuroService.loadFile(any(), any())).thenReturn(Mono.just(filePath));
        when(tsubakuroService.commitTx(any())).thenReturn(expectJob);

        when(fileSystemService.convertToDownloadPath(anyString(), anyString())).thenReturn(Path.of("/file1.csv"));
        when(fileSystemService.convertToAbsolutePath(anyString(), anyString())).thenReturn(path);
        when(fileSystemService.createTempDirectory(any())).thenCallRealMethod();

        var param = new LoadRequestParam();
        param.setJobId(jobId);
        param.setUid(uid);
        param.setTable(table);
        param.setFormat(DumpLoadRequestParam.FORMAT_DETECT_BY_EXTENSION);
        param.setFiles(pathList);

        Mono<TransactionalJob> actualResult = dumpLoadService.startLoad(param);

        Thread.sleep(1 * 1000L);
        var actualJob = actualResult.block();

        // erase temp dir to match result
        actualJob.setTempDir(null);
        assertEquals(expectJob, actualJob);

        var fromJobManager = dumpLoadService.getJob(Job.TYPE_LOAD, uid, jobId);
        assertEquals(expectJob, fromJobManager.block());
    }

    @Test
    public void test_startLoad_fail() throws Exception {

        var jobId = "jobId";
        var uid = "test_user";
        var table = "FOO_TBL";
        String filePath = "dump1/file1.txt";
        String errorMessage = "dummy error";

        var now = Instant.parse("2022-06-30T12:00:00.000Z");
        when(systemTime.now()).thenReturn(now);

        var expectJob = new LoadJob();
        expectJob.setType(Job.TYPE_LOAD);
        expectJob.setUid(uid);
        expectJob.setTable(table);
        expectJob.setJobId(jobId);
        expectJob.setStatus(JobStatus.FAILED);
        expectJob.setStartTime(now);
        expectJob.setEndTime(now);
        expectJob.setFormat(DumpLoadRequestParam.FORMAT_PARQUET);
        expectJob.setFiles(Arrays.asList(new String[] { filePath }));
        expectJob.setErrorMessage(errorMessage);
        expectJob.setMappings(Arrays.asList(new ColumnMapping[] {}));
        expectJob.setProgress(100);

        var pathList = new ArrayList<String>();
        pathList.add(filePath);
        when(tsubakuroService.createLoadTransaction(any())).thenThrow(new RuntimeException(errorMessage));

        var param = new LoadRequestParam();
        param.setJobId(jobId);
        param.setUid(uid);
        param.setTable(table);
        param.setFormat(DumpLoadRequestParam.FORMAT_PARQUET);
        param.setFiles(pathList);

        Mono<TransactionalJob> actualResult = dumpLoadService.startLoad(param);

        Thread.sleep(1 * 1000L);
        var actualJob = actualResult.block();

        assertEquals(expectJob, actualJob);

        var fromJobManager = dumpLoadService.getJob(Job.TYPE_LOAD, uid, jobId);
        assertEquals(expectJob, fromJobManager.block());
    }

    @Test
    public void test_getJob_not_found() throws Exception {
        var jobId = "jobId_not_found";
        var uid = "test_user";
        var now = Instant.parse("2022-06-30T12:00:00.000Z");
        when(systemTime.now()).thenReturn(now);

        try {
            dumpLoadService.getJob(Job.TYPE_DUMP, uid, jobId);
            fail("not thrown");
        } catch (NotFoundException ex) {
            // OK
        }
    }

    @Test
    public void test_getJobList() throws Exception {
        var uid = "test_user";
        var now = Instant.parse("2022-06-30T12:00:00.000Z");
        when(systemTime.now()).thenReturn(now);

        Flux<Job> actual = dumpLoadService.getJobList(Job.TYPE_LOAD, uid);
        StepVerifier.create(actual)
                .verifyComplete();

    }

    @Test
    public void test_cancelDump() throws Exception {

        var jobId = "jobId";
        var uid = "test_user";
        var dirPath = "dump1";
        var table = "FOO_TBL";

        var now = Instant.parse("2022-06-30T12:00:00.000Z");
        when(systemTime.now()).thenReturn(now);

        var expectJob = new DumpJob();
        expectJob.setType(Job.TYPE_DUMP);
        expectJob.setUid(uid);
        expectJob.setDirPath(dirPath);
        expectJob.setTable(table);
        expectJob.setJobId(jobId);
        expectJob.setStatus(JobStatus.CANCELED);
        expectJob.setStartTime(now);
        expectJob.setEndTime(now);
        expectJob.setFormat(DumpLoadRequestParam.FORMAT_PARQUET);
        expectJob.setProgress(100);

        // this flux is never finished.
        var testFlux = Flux.defer(() -> Flux.just(Path.of("file1.txt")));

        when(tsubakuroService.dumpTable(any())).thenReturn(testFlux);

        when(tsubakuroService.createDumpTransaction(any())).thenReturn(expectJob);

        var param = new DumpRequestParam();
        param.setJobId(jobId);
        param.setUid(uid);
        param.setDirPath(dirPath);
        param.setTable(table);
        param.setFormat(DumpLoadRequestParam.FORMAT_PARQUET);

        dumpLoadService.startDump(param);
        Mono<TransactionalJob> actualResult = dumpLoadService.cancelJob(Job.TYPE_DUMP, uid,
                jobId);

        Thread.sleep(1 * 1000L);
        var actualJob = actualResult.block();

        assertEquals(expectJob, actualJob);
    }

}
