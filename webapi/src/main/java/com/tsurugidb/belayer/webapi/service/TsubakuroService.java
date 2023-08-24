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

public interface TsubakuroService {

    BackupJob createBackupTransaction(BackupJob job);

    Flux<BackupContext> backupOnline(BackupJob job);

    DumpJob createDumpTransaction(DumpJob job);

    LoadJob createLoadTransaction(LoadJob job);

    TsurugiTransaction createTransaction(TransactionType transactionType, @Nonnull Credential credential, String jobId, Optional<Integer> timeoutMin, boolean needTransaction, String... tables );

    /**
     * dump all rows in specified table.
     * 
     * @param job DumpJob
     * @return absolute path list of generated dump files.
     */
    Flux<Path> dumpTable(DumpJob job);

    Mono<String> loadFile(LoadJob job, LoadFileInfo loadFileInfo);

    TransactionalJob commitTx(TransactionalJob job);

    TransactionalJob rollbackTx(TransactionalJob job);

    /**
     * list all table names.
     * 
     * @param credential credential for authentication.
     * @return list of table names.
     */
    List<String> listTables(String credential);

}
