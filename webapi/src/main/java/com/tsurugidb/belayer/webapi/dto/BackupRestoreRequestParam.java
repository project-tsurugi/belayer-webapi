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
import lombok.ToString;


/**
 * Parameters of a backup or restore request, combining the request body with the authenticated
 * user ({@code uid}, {@code credentials}) and the job ID.
 *
 * <p>{@code dirPath} is the backup output directory and {@code zipFilePath} the backup zip file
 * to restore from, both relative to the user's storage.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BackupRestoreRequestParam {

  private String uid;
  @ToString.Exclude
  private Object credentials;
  private String dirPath;
  private String zipFilePath;
  private String jobId;
}
