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
import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.tsurugidb.tsubakuro.exception.ServerException;
import com.tsurugidb.tsubakuro.sql.util.Load;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@Slf4j
public class TransactionalJob extends Job {

    @JsonIgnore
    private Path tempDir;

    @JsonIgnore
    private TsurugiTransaction tsurugiTransaction;

    @JsonIgnore
    private Load load;

    public void rollback() {
        if (tsurugiTransaction != null) {
            try {
                tsurugiTransaction.getTransaction().rollback().await();
            } catch (IOException | ServerException | InterruptedException ignore) {
                log.debug("ignore rollback exception", ignore);
                // ignore
            }
        }
    }

    @Override
    public void cancelJob() {
        rollback();
        super.cancelJob();
    }

    @Override
    public void close() {
        if (load != null) {
            try {
                load.close();
            } catch (ServerException | IOException | InterruptedException ignore) {
                log.warn("failed to close Load.", ignore);
            }
        }
        if (tsurugiTransaction != null) {
            tsurugiTransaction.close();
        }
        super.close();
    }

}
