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
package com.tsurugidb.belayer.webapi.exec;

import org.slf4j.Logger;

/**
 * Tells the external commands (the status script, tgctl_w and tgha_w) whether
 * to write their debug log, so that it follows the log level of this server
 * rather than being always on.
 */
final class ScriptDebug {

  /**
   * Environment variable that makes the external commands write their debug log.
   * Set to "true" only while the caller logs at DEBUG.
   */
  static final String ENV = "BELAYER_SCRIPT_DEBUG";

  private ScriptDebug() {
  }

  /**
   * set {@link #ENV} for the process if the logger is at DEBUG.
   *
   * @param pb process builder of the external command
   * @param log logger of the caller
   */
  static void apply(ProcessBuilder pb, Logger log) {
    if (log.isDebugEnabled()) {
      pb.environment().put(ENV, "true");
    }
  }
}
