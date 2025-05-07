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
package com.tsurugidb.belayer.webapi.config;

public enum FunctionPermission {
    P_NONE,
    P_FILE_LIST,
    P_UPLOAD,
    P_DOWNLOAD,
    P_FILE_DIR_DELETE,
    P_BACKUP,
    P_RESTORE,
    P_DUMP,
    P_LOAD,
    P_STREAM_API,
    P_SESSION_CTL,
    P_DB_START,
    P_DB_STOP,
    P_DB_STATUS,
    P_TABLE_LIST,
    P_ROLE_EDIT;
}
