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
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.codec.multipart.FilePart;

import com.tsurugidb.belayer.webapi.api.helper.UploadHelper.UploadParameter;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;

/**
 * Parameters of a stream load request (upload files and load them in an existing transaction).
 *
 * <p>{@code jobId} is the transaction ID, {@code table} the target table, {@code format} the input
 * file format, {@code fileParts} the uploaded files, {@code colMap} the column mappings and
 * {@code tempDir} the temporary working directory.</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class LoadParameter extends UploadParameter {
    private String jobId;
    private String format = DumpLoadRequestParam.FORMAT_DETECT_BY_EXTENSION;
    private String table;
    private List<FilePart> fileParts = new ArrayList<>();
    private List<ColumnMapping> colMap = new ArrayList<>();
    private LongTransactionJob transactionJob;
    private Path tempDir;

    public void addFilePart(FilePart filePart) {
        fileParts.add(filePart);
    }

    /**
     * Add a column mapping.
     *
     * @param colMapping ColumnMapping.
     */
    public void addColMapping(ColumnMapping colMapping) {
        colMap.add(colMapping);
    }
}
