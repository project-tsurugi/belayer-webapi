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

import java.nio.file.Path;

import com.fasterxml.jackson.annotation.JsonIgnore;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BackupJob extends Job {

    public BackupJob() {
        super();
        setType(TYPE_BACKUP);
    }

    private String output;
    @JsonIgnore
    private Path workDir;
    private String destDir;
    private String zipFilePath;

    @JsonIgnore
    private BackupTransaction backupTransaction;

    @Override
    public void close() {
        if (backupTransaction != null) {
            backupTransaction.close();
        }
        super.close();
    }

}
