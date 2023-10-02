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


@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class DumpLoadRequestParam {

  public static final String FORMAT_DETECT_BY_EXTENSION = "detect_by_ext";
  public static final String FORMAT_CSV = "csv";
  public static final String FORMAT_PARQUET = "parquet";
  public static final String FORMAT_ZIP = "zip";

  private String uid;
  private Object credentials;
  private String table;
  private String jobId;
  private String format = FORMAT_DETECT_BY_EXTENSION;
  private boolean waitUntilDone = false;
  
}
