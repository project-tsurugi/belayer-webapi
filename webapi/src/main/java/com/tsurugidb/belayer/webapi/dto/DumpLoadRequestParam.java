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
import lombok.ToString;


/**
 * Common parameters of dump and load requests.
 *
 * <p>Holds the authenticated user ({@code uid}, {@code credentials}), the target {@code table},
 * the {@code jobId}, the file {@code format} (one of the {@code FORMAT_*} values, default
 * {@link #FORMAT_DETECT_BY_EXTENSION}) and whether the API waits until the job is done
 * ({@code waitUntilDone}).</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public abstract class DumpLoadRequestParam {

  /** Format value: determine the file format from the file extension. */
  public static final String FORMAT_DETECT_BY_EXTENSION = "detect_by_ext";
  /** Format value: CSV. */
  public static final String FORMAT_CSV = "csv";
  /** Format value: Apache Parquet. */
  public static final String FORMAT_PARQUET = "parquet";
  /** Format value: zip archive of dump files. */
  public static final String FORMAT_ZIP = "zip";

  // set by the server, not by the client: hidden from the API document.
  @Schema(hidden = true)
  private String uid;
  @Schema(hidden = true)
  @ToString.Exclude
  private Object credentials;
  @Schema(hidden = true)
  private String table;
  @Schema(hidden = true)
  private String jobId;
  private String format = FORMAT_DETECT_BY_EXTENSION;
  private boolean waitUntilDone = false;
  
}
