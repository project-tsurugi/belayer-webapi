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
package com.tsurugidb.belayer.webapi.model;

/**
 * Contant definitions.
 */
public class Constants {

    /** File extension: Parquet */
    public static final String EXT_PARQUET = ".parquet";

    /** File extension: CSV */
    public  static final String EXT_CSV = ".csv";

    /** File extension: ZIP */
    public  static final String EXT_ZIP = ".zip";

    public static String TEMP_DIR_PREFIX_DOWNLOAD = "belayer-download-";

    public static String TEMP_DIR_PREFIX_DUMP = "belayer-dump-";

    public static String TEMP_DIR_PREFIX_LAOD = "belayer-load-";

    public static String TEMP_DIR_PREFIX_LAOD_ZIP = "belayer-load-zip-";

    public static String TEMP_DIR_PREFIX_BACKUP = "belayer-backup-";

    public static String TEMP_DIR_PREFIX_RESTORE = "belayer-restore-";

    public static String TEMP_DIR_PREFIX_MONITOR = "belayer-climonitor-";

}
