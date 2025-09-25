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
package com.tsurugidb.belayer.webapi.service.tsubakuro;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.tsurugidb.belayer.webapi.dto.BackupContext;
import com.tsurugidb.belayer.webapi.dto.BackupJob;
import com.tsurugidb.belayer.webapi.dto.BackupTransaction;
import com.tsurugidb.belayer.webapi.dto.DumpJob;
import com.tsurugidb.belayer.webapi.dto.LoadFileInfo;
import com.tsurugidb.belayer.webapi.dto.LoadJob;
import com.tsurugidb.belayer.webapi.dto.TransactionalJob;
import com.tsurugidb.belayer.webapi.dto.TsurugiTransaction;
import com.tsurugidb.belayer.webapi.exception.IORuntimeException;
import com.tsurugidb.belayer.webapi.exception.InterruptedRuntimeException;
import com.tsurugidb.belayer.webapi.service.FileSystemService;
import com.tsurugidb.belayer.webapi.service.TsubakuroService;
import com.tsurugidb.sql.proto.SqlRequest.TransactionType;
import com.tsurugidb.tsubakuro.channel.common.connection.Credential;
import com.tsurugidb.tsubakuro.channel.common.connection.RememberMeCredential;
import com.tsurugidb.tsubakuro.channel.common.connection.wire.Wire;
import com.tsurugidb.tsubakuro.common.Session;
import com.tsurugidb.tsubakuro.exception.ServerException;
import com.tsurugidb.tsubakuro.sql.SqlClient;
import com.tsurugidb.tsubakuro.sql.Transaction;
import com.tsurugidb.tsubakuro.util.FutureResponse;
import com.tsurugidb.tsubakuro.util.ServerResource;
import com.tsurugidb.tsubakuro.util.Timeout;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@Profile({ "ut", "ft" })
public class TsubakuroServiceStub implements TsubakuroService {

    @Autowired
    FileSystemService fileSystemService;

    @Value("${webapi.storage.root}")
    String storageRootDir;

    @Value("${webapi.db.mock.tablenames}")
    String tableNames;

    public BackupJob createBackupTransaction(BackupJob job) {
        log.debug("createBackupTransaction");
        var tran = BackupTransaction.builder()
                .jobId(job.getJobId())
                .session(new SessionStub())
                .datastoreClient(null)
                .backup(null)
                .build();
        job.setBackupTransaction(tran);
        return job;
    }

    public Flux<BackupContext> backupOnline(BackupJob job) {
        log.debug("called");
        var session = job.getBackupTransaction().getSession();
        List<BackupContext> backupFiles = List.of(
                new BackupContext(Path.of(storageRootDir, "backup", "backupFile1"), session),
                new BackupContext(Path.of(storageRootDir, "backup", "backupFile2"), session),
                new BackupContext(Path.of(storageRootDir, "backup", "backupFile3"), session));

        try {
            Thread.sleep(10 * 1000l);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return Flux.fromIterable(backupFiles);
    }

    public DumpJob createDumpTransaction(DumpJob job) {
        String credentials = (String) job.getCredentials();
        Objects.requireNonNull(credentials);
        var cred = new RememberMeCredential(credentials);
        var tx = createTransaction(TransactionType.READ_ONLY, cred, job.getJobId(), "dump", Optional.empty(), true, job.getTable());
        job.setTsurugiTransaction(tx);

        return job;
    }

    public LoadJob createLoadTransaction(LoadJob job) {
        String credentials = (String) job.getCredentials();
        Objects.requireNonNull(credentials);
        var cred = new RememberMeCredential(credentials);
        var tx = createTransaction(TransactionType.LONG, cred, job.getJobId(), "load", Optional.empty(), job.isTransactionNeeded(), job.getTable());
        job.setTsurugiTransaction(tx);

        return job;
    }

    public TsurugiTransaction createTransaction(TransactionType transactionType, @Nonnull Credential credential, String jobId, String label,
            Optional<Integer> timeoutMin, boolean needTransaction, String... tables) {
        log.debug("called: createTransaction()");
        return TsurugiTransaction.builder()
                .jobId(jobId)
                .session(new SessionStub())
                .sqlClient(new SqlClientStub())
                .transaction(new TransactionStub())
                .build();
    }

    public Flux<Path> dumpTable(DumpJob job) {
        log.debug("called: dumpTable()");

        if (job.getTable().indexOf("_BAD_") > -1) {
            throw new IORuntimeException("_BAD_.", null);
        }

        Path outDirPath = fileSystemService.convertToAbsolutePath(job.getUid(),
                job.getDirPath() + "/" + job.getJobId());
        try {
            Files.createDirectories(outDirPath);
        } catch (IOException ex) {
            throw new IORuntimeException("Failed to create output dir.", ex);

        }
        List<Path> dumpFiles = List.of(
                Path.of(storageRootDir, "dump", "dumpFile1.parquet"),
                Path.of(storageRootDir, "dump", "dumpFile2.parquet"),
                Path.of(storageRootDir, "dump", "dumpFile3.parquet"));

        Flux<Path> flux = Flux.create((sink) -> {

            for (Path dumpFile : dumpFiles) {
                Path targetFile = dumpFile.toAbsolutePath().normalize();
                Path destPath = Path.of(outDirPath.toString(), dumpFile.getFileName().toString()).toAbsolutePath()
                        .normalize();
                try {
                    log.debug("copy {}->{}", targetFile, destPath);
                    Thread.sleep(3 * 1000l);
                    Files.copy(targetFile, destPath, StandardCopyOption.REPLACE_EXISTING);

                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (IOException ex) {
                    log.debug("failed to copy dump file.", ex);
                    Path downloadPath = fileSystemService.convertToDownloadPath(job.getUid(), destPath.toString());
                    throw new IORuntimeException("Failed to copy dump file.:" + downloadPath.toString(), ex);
                }

                sink.next(destPath);
            }

            sink.complete();
        });
        return flux;
    }

    public Mono<String> loadFile(LoadJob job, LoadFileInfo loadFileInfo) {
        var dumpFilePath = loadFileInfo.getFilePath();
        Path downloadPath = fileSystemService.convertToDownloadPath(job.getUid(), dumpFilePath.toString());
        log.debug("called: loadFile():" + downloadPath);

        if (job.getTable().indexOf("_BAD_") > -1) {
            return Mono.error(new Exception("_BAD_"));
        }

        try {
            Thread.sleep(10 * 1000l);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return Mono.just(downloadPath.toString());
    }

    public TransactionalJob commitTx(TransactionalJob job) {
        log.debug("called: commitTx()");
        try {
            job.getTsurugiTransaction().getTransaction().commit().await();
            return job;

        } catch (IOException | ServerException ex) {
            throw new IORuntimeException("failed to commit transaction.", ex);
        } catch (InterruptedException ex) {
            throw new InterruptedRuntimeException("failed to commit transaction.", ex);
        }
    }

    public TransactionalJob rollbackTx(TransactionalJob job) {
        log.debug("called: rollbackTx()");
        try {
            job.getTsurugiTransaction().getTransaction().rollback().await();
        } catch (IOException | ServerException | InterruptedException ignore) {
            log.warn("rollback failed. ignore this.", ignore);
        }
        return job;

    }

    public List<String> listTables(String credential) {
        Objects.requireNonNull(tableNames);
        return Arrays.stream(tableNames.split(",")).map(name -> name.trim()).collect(Collectors.toList());
    }

    public static class SessionStub implements Session {

        @Override
        public void close() throws ServerException, IOException, InterruptedException {
            log.debug("called: SessionStub#close()");
        }

        @Override
        public FutureResponse<Void> updateExpirationTime(long time, @Nonnull TimeUnit unit) throws IOException {
            return new FutureResponse<Void>() {

                @Override
                public boolean isDone() {
                    return true;
                }

                @Override
                public Void get() throws IOException, ServerException, InterruptedException {
                    return null;
                }

                @Override
                public Void get(long timeout, TimeUnit unit) {
                    return null;
                }

                @Override
                public void close() throws IOException, ServerException, InterruptedException {
                }
            };
        }

        @Override
        public void connect(Wire sessionWire) {
        }

        @Override
        public Wire getWire() {
            return null;
        }

        @Override
        public Timeout getCloseTimeout() {
            return null;
        }

        @Override
        public void put(@Nonnull ServerResource resource) {
        }

        @Override
        public void remove(@Nonnull ServerResource resource) {
        }
    }

    public class SqlClientStub implements SqlClient {
        @Override
        public void close() throws ServerException, IOException, InterruptedException {
            log.debug("called: SqlClientStub#close()");
        }
    }

    public class TransactionStub implements Transaction {

        @Override
        public FutureResponse<Void> commit() throws IOException {
            log.debug("called: TransactionStub#commit()");
            return new FutureResponseStub();
        }

        @Override
        public FutureResponse<Void> rollback() throws IOException {
            log.debug("called: TransactionStub#rollback()");
            return new FutureResponseStub();
        }

        @Override
        public void close() throws ServerException, IOException, InterruptedException {
            log.debug("called: TransactionStub#close()");
            return;
        }

    }

    private class FutureResponseStub implements FutureResponse<Void> {

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public Void get() throws IOException, ServerException, InterruptedException {
            return null;
        }

        @Override
        public Void get(long timeout, TimeUnit unit)
                throws IOException, ServerException, InterruptedException, TimeoutException {
            return null;
        }

        @Override
        public void close() throws IOException, ServerException, InterruptedException {
            log.debug("called: FutureResponseStub#close()");
        }
    }

}
