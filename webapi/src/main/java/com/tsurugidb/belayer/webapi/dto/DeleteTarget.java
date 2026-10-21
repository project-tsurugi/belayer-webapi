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

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request and response body of the file/directory delete APIs.
 *
 * <ul>
 * <li>{@code uid}: user ID, set from the authenticated user</li>
 * <li>{@code path}: file or directory to delete (single-target APIs)</li>
 * <li>{@code pathList}: files to delete (multiple-file API)</li>
 * <li>{@code force}: if {@code true}, a directory is deleted together with its contents</li>
 * </ul>
 *
 * <p>Paths are relative to the user's storage directory.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteTarget {

    private String uid;
    private String path;
    private String[] pathList;
    private boolean force = false;
}
