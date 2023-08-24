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
package com.tsurugidb.belayer.webapi.dto;

import java.io.IOException;

import com.tsurugidb.tsubakuro.common.Session;
import com.tsurugidb.tsubakuro.exception.ServerException;
import com.tsurugidb.tsubakuro.sql.SqlClient;
import com.tsurugidb.tsubakuro.sql.Transaction;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@Slf4j
public class TsurugiTransaction implements AutoCloseable {

    private String jobId;
    private Session session;
    private SqlClient sqlClient;
    private Transaction transaction;

    /**
     * release resources.
     */
    @Override
    public void close() {
        log.debug("close Transaction. jobId:" + jobId);

        if (transaction != null) {
            try {
                transaction.close();
            } catch (IOException | InterruptedException | ServerException ex) {
                // ignore
                log.debug("close transaction", ex);
            }
        }
        if (sqlClient != null) {
            try {
                sqlClient.close();
            } catch (IOException | InterruptedException | ServerException ex) {
                // ignore
                log.debug("close client", ex);
            }
        }
        if (session != null) {
            try {
                session.close();
            } catch (IOException | InterruptedException | ServerException ex) {
                // ignore
                log.debug("close session", ex);
            }
        }

    }
}
