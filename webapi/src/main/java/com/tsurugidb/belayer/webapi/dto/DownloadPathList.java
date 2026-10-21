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

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body holding a list of file paths ({@code fileNames}), returned by the upload API
 * (uploaded files) and the directory list API (entries of the directory; directories end with {@code /}).
 *
 * <p>{@code message} is set when the directory list was truncated, otherwise {@code null}.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownloadPathList {

    private List<String> fileNames;
    private String message;
}
