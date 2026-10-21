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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.ToString;

/**
 * A file to load. {@code filePath} is the Parquet file actually loaded; when the input was CSV
 * ({@code fromCsv} is {@code true}) it is the converted file and {@code originalFilePath} is the CSV file.
 */
@Data
@AllArgsConstructor
@ToString
public class LoadFileInfo {

    private Path filePath;
    private Path originalFilePath;
    private boolean fromCsv;
    
}
