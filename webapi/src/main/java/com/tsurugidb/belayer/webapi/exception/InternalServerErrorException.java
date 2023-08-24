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
package com.tsurugidb.belayer.webapi.exception;

import org.springframework.http.HttpStatus;

public class InternalServerErrorException extends BelayerResponseStatusException {
  
  public InternalServerErrorException(String reason, Throwable cause) {
    super(HttpStatus.INTERNAL_SERVER_ERROR, "Internal Error caused on the server", reason, cause);
  }
  
}
