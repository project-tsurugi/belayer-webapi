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

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nonnull;

import com.tsurugidb.belayer.webapi.dto.BackupContext;
import com.tsurugidb.belayer.webapi.dto.BackupJob;
import com.tsurugidb.belayer.webapi.dto.DumpJob;
import com.tsurugidb.belayer.webapi.dto.LoadFileInfo;
import com.tsurugidb.belayer.webapi.dto.LoadJob;
import com.tsurugidb.belayer.webapi.dto.TransactionalJob;
import com.tsurugidb.belayer.webapi.dto.TsurugiTransaction;
import com.tsurugidb.sql.proto.SqlRequest.TransactionType;
import com.tsurugidb.tsubakuro.channel.common.connection.Credential;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service to access Tsurugi DB via Tsubakuro.
 */
public interface TsubakuroService {

    /**
     * begin an online backup and set the backup transaction to the job.
     *
     * @param job BackupJob
     * @return the given job.
     */
    BackupJob createBackupTransaction(BackupJob job);

    /**
     * get the files to back up from the backup transaction of the job.
     *
     * @param job BackupJob with a backup transaction.
     * @return contexts of the files to back up.
     */
    Flux<BackupContext> backupOnline(BackupJob job);

    /**
     * create a read only transaction for dump and set it to the job.
     *
     * @param job DumpJob
     * @return the given job.
     */
    DumpJob createDumpTransaction(DumpJob job);

    /**
     * create a long transaction for load and set it to the job.
     *
     * @param job LoadJob
     * @return the given job.
     */
    LoadJob createLoadTransaction(LoadJob job);

    /**
     * create a session and a SQL client, and start a transaction if required.
     *
     * @param transactionType type of the transaction.
     * @param credential      credential for authentication.
     * @param jobId           Job ID
     * @param label           label of the session and the transaction.
     * @param timeoutMin      session timeout in minutes. the configured value is used if empty.
     * @param needTransaction if false, only the session and the SQL client are created.
     * @param tables          write preserve tables for a long transaction.
     * @return created transaction object.
     */
    TsurugiTransaction createTransaction(TransactionType transactionType, @Nonnull Credential credential, String jobId, String label, Optional<Integer> timeoutMin, boolean needTransaction, String... tables );

    /**
     * dump all rows in specified table.
     * 
     * @param job DumpJob
     * @return absolute path list of generated dump files.
     */
    Flux<Path> dumpTable(DumpJob job);

    /**
     * load a dump file into the table of the job.
     *
     * @param job          LoadJob
     * @param loadFileInfo information of the file to load.
     * @return download path of the original file.
     */
    Mono<String> loadFile(LoadJob job, LoadFileInfo loadFileInfo);

    /**
     * commit the transaction of the job, if any.
     *
     * @param job job with a transaction.
     * @return the given job.
     */
    TransactionalJob commitTx(TransactionalJob job);

    /**
     * roll back the transaction of the job, if any.
     *
     * @param job job with a transaction.
     * @return the given job.
     */
    TransactionalJob rollbackTx(TransactionalJob job);

    /**
     * list all table names.
     * 
     * @param credential credential for authentication.
     * @return list of table names.
     */
    List<String> listTables(String credential);

}
