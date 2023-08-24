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
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;

import com.tsurugidb.belayer.webapi.dto.BackupContext;
import com.tsurugidb.belayer.webapi.dto.BackupJob;
import com.tsurugidb.belayer.webapi.dto.BackupRestoreRequestParam;
import com.tsurugidb.belayer.webapi.dto.Job;
import com.tsurugidb.belayer.webapi.dto.Job.JobStatus;
import com.tsurugidb.belayer.webapi.dto.RestoreJob;
import com.tsurugidb.belayer.webapi.exception.BadRequestException;
import com.tsurugidb.belayer.webapi.exception.IORuntimeException;
import com.tsurugidb.belayer.webapi.exception.NotFoundException;
import com.tsurugidb.belayer.webapi.exec.DbQuiesceExec;
import com.tsurugidb.belayer.webapi.exec.DbRestoreExec;
import com.tsurugidb.belayer.webapi.exec.DbStatusExec;
import com.tsurugidb.belayer.webapi.exec.OfflineBackupExec;
import com.tsurugidb.belayer.webapi.model.Constants;
import com.tsurugidb.belayer.webapi.model.SystemTime;
import com.tsurugidb.belayer.webapi.service.tsubakuro.TsubakuroServiceStub.SessionStub;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "webapi.storage.root=./test_tmp")
public class BackupRestoreServiceTest {

    @Value("${webapi.storage.root}")
    private String storageRootDir;

    @Autowired
    BackupRestoreService backupRestoreService;

    @MockBean
    TsubakuroService tsubakuroService;

    @MockBean
    DbStatusExec dbStatusExec;

    @MockBean
    DbQuiesceExec dbQuiesceExec;

    @MockBean
    OfflineBackupExec offlineBackupExec;

    @MockBean
    DbRestoreExec dbRestoreExec;

    @MockBean
    SystemTime systemTime;

    @SpyBean
    FileSystemService fileSystemService;

    String testDir = "./src/test/files/backup_restore/";
    String jobId = "jid_abcde";

    @BeforeEach
    public void setUp() throws IOException {
        // create dir for test
        Files.createDirectories(Path.of(testDir));
        Files.createDirectories(Path.of(storageRootDir));
    }

    @AfterEach
    public void tearDown() throws IOException {
        // delete dir for test
        Files.walk(Path.of(testDir))
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);

        Files.walk(Path.of(storageRootDir))
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    @Test
    public void test_startBackup_online() throws Exception {

        var uid = "test_user";
        var dirPath = "bk1";

        var backupFiles = List.of(new BackupContext(Path.of(testDir + "file1.txt"), new SessionStub()));

        var now = Instant.parse("2022-06-30T12:00:00.000Z");
        when(systemTime.now()).thenReturn(now);

        var expectJob = new BackupJob();
        expectJob.setType(Job.TYPE_BACKUP);
        expectJob.setUid(uid);
        expectJob.setDestDir(dirPath);
        expectJob.setJobId(jobId);
        expectJob.setStatus(JobStatus.COMPLETED);
        expectJob.setStartTime(now);
        expectJob.setEndTime(now);
        expectJob.setOutput(null);
        expectJob.setWorkDir(Path.of(testDir, Constants.TEMP_DIR_PREFIX_BACKUP + jobId));
        expectJob.setZipFilePath("bk1/backup-" + jobId + ".zip");

        when(dbStatusExec.isOnline(jobId)).thenReturn(true);
        for (BackupContext backupCtx : backupFiles) {
            Files.write(backupCtx.getTargetFilePath(), "test".getBytes());
        }
        when(tsubakuroService.backupOnline(any())).thenReturn(Flux.fromIterable(backupFiles));
        when(tsubakuroService.createBackupTransaction(any())).thenReturn(expectJob);
        when(fileSystemService.createTempDirectory(any())).thenAnswer(new Answer<Path>() {
            @Override
            public Path answer(InvocationOnMock invoke) {
                String dir = invoke.getArgument(0, String.class);
                try {
                    Path workDirPath = Path.of(testDir, dir);
                    Files.createDirectories(workDirPath);
                    return workDirPath;
                } catch (IOException ex) {
                    throw new IORuntimeException("I/O error", ex);
                }
            }
        });

        var param = new BackupRestoreRequestParam();
        param.setJobId(jobId);
        param.setUid(uid);
        param.setDirPath(dirPath);

        Mono<BackupJob> actualResult = backupRestoreService.startBackup(param);

        Thread.sleep(1 * 1000L);
        var actualJob = actualResult.block();
        assertEquals(expectJob, actualJob);

        var fromJobManager = backupRestoreService.getJob(Job.TYPE_BACKUP, uid, jobId);
        assertEquals(expectJob, fromJobManager.block());

    }

    @Test
    public void test_startBackup_online_error() throws Exception {

        var uid = "test_user";
        var dirPath = "bk1";

        var errorMessage = "test error";

        when(dbStatusExec.isOnline(jobId)).thenReturn(true);
        var pathList = new ArrayList<Path>();
        pathList.add(Path.of("./src/test/files/backup_restore/file1.txt"));
        when(tsubakuroService.createBackupTransaction(any())).thenThrow(new RuntimeException(errorMessage));
        when(tsubakuroService.backupOnline(any())).thenThrow(new RuntimeException(errorMessage));
        when(fileSystemService.createTempDirectory(any())).thenAnswer(new Answer<Path>() {
            @Override
            public Path answer(InvocationOnMock invoke) {
                String dir = invoke.getArgument(0, String.class);
                try {
                    Path workDirPath = Path.of(testDir, dir);
                    Files.createDirectories(workDirPath);
                    return workDirPath;
                } catch (IOException ex) {
                    throw new IORuntimeException("I/O error", ex);
                }
            }
        });

        var now = Instant.parse("2022-06-30T12:00:00.000Z");
        when(systemTime.now()).thenReturn(now);

        var param = new BackupRestoreRequestParam();
        param.setJobId(jobId);
        param.setUid(uid);
        param.setDirPath(dirPath);

        Mono<BackupJob> actualResult = backupRestoreService.startBackup(param);

        var expectJob = new BackupJob();
        expectJob.setType(Job.TYPE_BACKUP);
        expectJob.setUid(uid);
        expectJob.setDestDir(dirPath);
        expectJob.setJobId(jobId);
        expectJob.setStatus(JobStatus.FAILED);
        expectJob.setStartTime(now);
        expectJob.setEndTime(now);
        expectJob.setErrorMessage(errorMessage);
        expectJob.setWorkDir(Path.of(testDir, Constants.TEMP_DIR_PREFIX_BACKUP + jobId));

        Thread.sleep(1 * 1000L);
        var actualJob = actualResult.block();

        assertEquals(expectJob, actualJob);

        var fromJobManager = backupRestoreService.getJob(Job.TYPE_BACKUP, uid, jobId);
        assertEquals(expectJob, fromJobManager.block());

    }

    @Test
    public void test_startBackup_offline() throws Exception {

        when(dbStatusExec.isOnline(anyString())).thenReturn(false);
        doNothing().when(dbQuiesceExec).callQuiesce(any());

        var now = Instant.parse("2022-06-30T12:00:00.000Z");
        Mockito.when(systemTime.now()).thenReturn(now);

        var uid = "test_user";
        var dirPath = "bk1";

        var expectJob = new BackupJob();
        expectJob.setType(Job.TYPE_BACKUP);
        expectJob.setUid(uid);
        expectJob.setDestDir(dirPath);
        expectJob.setJobId(jobId);
        expectJob.setStatus(JobStatus.COMPLETED);
        expectJob.setStartTime(now);
        expectJob.setEndTime(now);
        expectJob.setWorkDir(Path.of(testDir,  Constants.TEMP_DIR_PREFIX_BACKUP + jobId));
        expectJob.setZipFilePath(dirPath + "/backup-" + jobId + ".zip");


        when(fileSystemService.createTempDirectory(any())).thenAnswer(new Answer<Path>() {
            @Override
            public Path answer(InvocationOnMock invoke) {
                String dir = invoke.getArgument(0, String.class);
                try {
                    Path workDirPath = Path.of(testDir, dir);
                    Files.createDirectories(workDirPath);
                    return workDirPath;
                } catch (IOException ex) {
                    throw new IORuntimeException("I/O error", ex);
                }
            }
        });

        when(offlineBackupExec.backupOffline(any())).thenReturn(expectJob);

        var param = new BackupRestoreRequestParam();
        param.setJobId(jobId);
        param.setUid(uid);
        param.setDirPath(dirPath);

        Mono<BackupJob> actualResult = backupRestoreService.startBackup(param);

        Thread.sleep(1 * 1000L);
        var actualJob = actualResult.block();

        assertEquals(expectJob, actualJob);
    }

    @Test
    public void test_startRestore() throws Exception {

        var uid = "test_user";
        var zipFilePath = "bk1/backup.zip";

        var zipFile = Path.of(storageRootDir, uid, zipFilePath);
        Files.createDirectories(zipFile.getParent());

        Files.copy(Path.of("./src/test/files/test.zip"), zipFile);

        var job = new RestoreJob();
        job.setType(Job.TYPE_RESTORE);
        job.setJobId(jobId);
        job.setUid(uid);

        when(dbStatusExec.isOnline(anyString())).thenReturn(false);

        when(fileSystemService.createTempDirectory(any())).thenAnswer(new Answer<Path>() {
            @Override
            public Path answer(InvocationOnMock invoke) {
                String dir = invoke.getArgument(0, String.class);
                try {
                    Path workDirPath = Path.of(testDir, dir);
                    Files.createDirectories(workDirPath);
                    return workDirPath;
                } catch (IOException ex) {
                    throw new IORuntimeException("I/O error", ex);
                }
            }
        });

        when(dbRestoreExec.startRestore(any())).thenReturn(job);

        var now = Instant.parse("2022-06-30T12:00:00.000Z");
        Mockito.when(systemTime.now()).thenReturn(now);

        var param = new BackupRestoreRequestParam();
        param.setJobId(jobId);
        param.setUid(uid);
        param.setZipFilePath(zipFilePath);

        Mono<RestoreJob> actualResult = backupRestoreService.startRestore(param);

        var expectJob = new RestoreJob();
        expectJob.setType(Job.TYPE_RESTORE);
        expectJob.setUid(uid);
        expectJob.setJobId(jobId);
        expectJob.setStatus(JobStatus.COMPLETED);
        expectJob.setStartTime(now);
        expectJob.setEndTime(now);
        expectJob.setZipFilePath(zipFilePath);
        expectJob.setWorkDir(Path.of(testDir + Constants.TEMP_DIR_PREFIX_RESTORE + jobId));

        Thread.sleep(1 * 1000L);
        var actualJob = actualResult.block();

        assertEquals(expectJob, actualJob);
    }

    @Test
    public void test_startRestore_in_online() throws Exception {

        when(dbStatusExec.isOnline(anyString())).thenReturn(true);

        var uid = "test_user";
        var zipFilePath = "bk1/backup.zip";
        var param = new BackupRestoreRequestParam();
        param.setJobId(jobId);
        param.setUid(uid);
        param.setZipFilePath(zipFilePath);

        try {
            backupRestoreService.startRestore(param);
            fail("not thrown");
        } catch (BadRequestException ex) {
            assertEquals("DB is online.", ex.getDisplayMessage());
        }
    }

    @Test
    public void test_getJob_not_found() throws Exception {
        var uid = "test_user";
        var now = Instant.parse("2022-06-30T12:00:00.000Z");
        when(systemTime.now()).thenReturn(now);

        try {
            backupRestoreService.getJob(Job.TYPE_RESTORE, uid, jobId);
            fail("not thrown");
        } catch (NotFoundException ex) {
            // OK
        }
    }

    @Test
    public void test_getJobList() throws Exception {
        var uid = "test_user";

        Flux<Job> actual = backupRestoreService.getJobList(Job.TYPE_RESTORE, uid);
        StepVerifier.create(actual)
                .verifyComplete();

    }

    @Test
    public void test_cancelBackup() throws Exception {

        var uid = "test_user";
        var dirPath = "bk1";

        when(dbStatusExec.isOnline(jobId)).thenReturn(true);
        var pathList = new ArrayList<Path>();
        pathList.add(Path.of("./src/test/files/backup_restore/file1.txt"));

        // this flux is never finished.
        var testFlux = Flux.defer(() -> Flux.just(new BackupContext(Path.of("file1.txt"), null)));

        when(tsubakuroService.backupOnline(any())).thenReturn(testFlux);
        when(fileSystemService.createTempDirectory(any())).thenAnswer(new Answer<Path>() {
            @Override
            public Path answer(InvocationOnMock invoke) {
                String dir = invoke.getArgument(0, String.class);
                try {
                    Path workDirPath = Path.of(dir);
                    Files.createDirectories(workDirPath);
                    return workDirPath;
                } catch (IOException ex) {
                    throw new IORuntimeException("I/O error", ex);
                }
            }
        });

        var now = Instant.parse("2022-06-30T12:00:00.000Z");
        when(systemTime.now()).thenReturn(now);

        // when(fileSystemService.copyToDir(any(), anyString(),
        // anyString())).thenReturn(dirPath + "/file1.txt");

        var param = new BackupRestoreRequestParam();
        param.setJobId(jobId);
        param.setUid(uid);
        param.setDirPath(dirPath);

        backupRestoreService.startBackup(param);
        Mono<Job> actualResult = backupRestoreService.cancelBackupRestoreJob(Job.TYPE_BACKUP,
                uid,
                jobId);

        var expectJob = new BackupJob();
        expectJob.setType(Job.TYPE_BACKUP);
        expectJob.setUid(uid);
        expectJob.setDestDir(dirPath);
        expectJob.setJobId(jobId);
        expectJob.setStatus(JobStatus.CANCELED);
        expectJob.setStartTime(now);
        expectJob.setEndTime(now);
        expectJob.setWorkDir(Path.of(Constants.TEMP_DIR_PREFIX_BACKUP + jobId));

        Thread.sleep(1 * 1000L);
        var actualJob = actualResult.block();

        assertEquals(expectJob, actualJob);
    }

}
