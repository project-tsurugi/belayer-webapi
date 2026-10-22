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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Hides the authentication token in the command line of an external command,
 * so that the command line can be written to the log.
 */
final class CommandLineMask {

  /** Option of the external commands that takes the authentication token. */
  static final String AUTH_TOKEN_OPTION = "--auth-token";

  /** Replacement of the token. */
  static final String MASK = "********";

  private CommandLineMask() {
  }

  /**
   * return a copy of the arguments with the value of {@value #AUTH_TOKEN_OPTION} masked.
   *
   * Both "--auth-token TOKEN" and "--auth-token=TOKEN" are masked.
   *
   * @param args arguments of the command (not modified)
   * @return arguments to log
   */
  static List<String> mask(List<String> args) {
    List<String> masked = new ArrayList<>(args.size());
    boolean maskNext = false;
    for (String arg : args) {
      if (maskNext) {
        masked.add(MASK);
        maskNext = false;
      } else if (AUTH_TOKEN_OPTION.equals(arg)) {
        masked.add(arg);
        maskNext = true;
      } else if (arg != null && arg.startsWith(AUTH_TOKEN_OPTION + "=")) {
        masked.add(AUTH_TOKEN_OPTION + "=" + MASK);
      } else {
        masked.add(arg);
      }
    }
    return masked;
  }

  /**
   * return a copy of the arguments with the value of {@value #AUTH_TOKEN_OPTION} masked.
   *
   * @param args arguments of the command (not modified)
   * @return arguments to log
   * @see #mask(List)
   */
  static List<String> mask(String[] args) {
    return mask(Arrays.asList(args));
  }
}
