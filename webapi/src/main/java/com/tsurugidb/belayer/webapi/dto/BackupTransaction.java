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
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.tsurugidb.tsubakuro.common.Session;
import com.tsurugidb.tsubakuro.datastore.Backup;
import com.tsurugidb.tsubakuro.datastore.DatastoreClient;
import com.tsurugidb.tsubakuro.exception.ServerException;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@Slf4j
public class BackupTransaction implements AutoCloseable {

    private String jobId;
    private Session session;
    private DatastoreClient datastoreClient;
    private Backup backup;

    /**
     * return Path of files to backup.
     * 
     * @return Path of files and current session.
     */
    public List<BackupContext> getBackupFilePaths() {
        Objects.requireNonNull(backup);
        var list = new ArrayList<BackupContext>();
        for (Path path : backup.getFiles()) {
            list.add(new BackupContext(path, session));
        }

        return list;
    }

    /**
     * release resources.
     */
    @Override
    public void close() {
        log.debug("close backup jobId:" + jobId);

        if (backup != null) {
            try {
                backup.close();
            } catch (IOException | InterruptedException | ServerException ex) {
                // ignore
                log.debug("close backup", ex);
            }
        }

        if (datastoreClient != null) {
            try {
                datastoreClient.close();
            } catch (IOException | InterruptedException | ServerException ex) {
                // ignore
                log.debug("close datastoreClient", ex);

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
