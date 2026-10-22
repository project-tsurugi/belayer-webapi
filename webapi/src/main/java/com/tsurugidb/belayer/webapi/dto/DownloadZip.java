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

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body of the zip download API: {@code pathList} holds the files to put into the zip,
 * relative to the user's storage directory. {@code uid} is set from the authenticated user.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DownloadZip {
    // set by the server, not by the client: hidden from the API document.
    @Schema(hidden = true)
    private String uid;
    private String[] pathList;
}
