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

/**
 * Permissions required to call the API endpoints.
 *
 * <p>
 * Each permission is mapped to roles in {@code permission.properties}.
 * </p>
 */
public enum FunctionPermission {
    /** No permission required. Endpoints with this permission are permitted to all. */
    P_NONE,
    /** Listing files and directories in the storage area on the server. */
    P_FILE_LIST,
    /** Uploading files to the storage area on the server. */
    P_UPLOAD,
    /** Downloading files from the storage area on the server. */
    P_DOWNLOAD,
    /** Deleting files and directories in the storage area on the server. */
    P_FILE_DIR_DELETE,
    /** Running backups. */
    P_BACKUP,
    /** Running restores. */
    P_RESTORE,
    /** Dumping tables to files. */
    P_DUMP,
    /** Loading dump files into tables. */
    P_LOAD,
    /** Dumping and loading within a single transaction using the Stream API. */
    P_STREAM_API,
    /** Showing session status, setting session variables, and killing sessions. */
    P_SESSION_CTL,
    /** Listing the endpoints. */
    P_LIST_ENDPOINTS,
    /** Starting the database. */
    P_DB_START,
    /** Stopping the database. */
    P_DB_STOP,
    /** Changing the database mode. */
    P_DB_CHANGE_MODE,
    /** Instructing the database to synchronize its WAL. */
    P_DB_SYNC,
    /** Showing the database status. */
    P_DB_STATUS,
    /** Listing the table names in the database. */
    P_TABLE_LIST,
    /** Showing role definitions, and showing and updating role-user mappings. */
    P_ROLE_EDIT;
}
