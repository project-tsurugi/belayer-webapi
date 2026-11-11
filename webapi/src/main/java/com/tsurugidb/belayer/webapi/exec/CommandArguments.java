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
import java.util.List;
import java.util.Objects;

/**
 * Builds the arguments of an external command from a command template, and
 * passes the authentication token to it.
 *
 * The template (a {@code webapi.cli.cmd.*} property) is split into words at
 * whitespace first, and only then are the {@code %s} placeholders filled. A
 * value therefore always stays within its own argument, whatever characters it
 * holds; the command is started without a shell, so nothing in a value is
 * interpreted. Templates do not quote their placeholders for the same reason.
 */
final class CommandArguments {

  /** Environment variable through which tgctl (and tgha) take the authentication token. */
  static final String AUTH_TOKEN_ENV = "TSURUGI_AUTH_TOKEN";

  private static final String PLACEHOLDER = "%s";

  private CommandArguments() {
  }

  /**
   * build the arguments from the template.
   *
   * The placeholders are filled with the values in order, like
   * {@link String#format(String, Object...)}: values left over are ignored, so
   * a template may use fewer of them than the caller passes. A word that is
   * exactly {@code %s} and whose value is a {@link List} becomes one argument
   * per element of the list (for options that come as a group, such as a
   * launch mode and its options).
   *
   * @param template command template; the words are separated by whitespace
   * @param values values of the placeholders, a String or a List of String each
   * @return arguments of the command
   * @throws IllegalArgumentException if the template has more placeholders than values,
   *     or a list is given to a placeholder that is not a whole word
   */
  static List<String> build(String template, Object... values) {
    Objects.requireNonNull(template);
    List<String> args = new ArrayList<>();
    int next = 0;
    for (String word : template.trim().split("\\s+")) {
      if (word.isEmpty()) {
        continue;
      }
      if (word.equals(PLACEHOLDER) && next < values.length && values[next] instanceof List) {
        for (Object element : (List<?>) values[next++]) {
          args.add(String.valueOf(element));
        }
        continue;
      }
      StringBuilder arg = new StringBuilder();
      int from = 0;
      int at;
      while ((at = word.indexOf(PLACEHOLDER, from)) >= 0) {
        if (next >= values.length) {
          throw new IllegalArgumentException("the command template has more placeholders than values: " + template);
        }
        Object value = values[next++];
        if (value instanceof List) {
          throw new IllegalArgumentException("a list is given to a placeholder that is not a whole word: " + word);
        }
        arg.append(word, from, at).append(value);
        from = at + PLACEHOLDER.length();
      }
      arg.append(word.substring(from));
      args.add(arg.toString());
    }
    return args;
  }

  /**
   * pass the authentication token to the command through {@value #AUTH_TOKEN_ENV}.
   *
   * The token is not put on the command line, where other users of the host
   * could read it.
   *
   * @param pb process builder of the external command
   * @param token authentication token (nothing is set if null or empty)
   */
  static void setAuthToken(ProcessBuilder pb, String token) {
    if (token != null && !token.isEmpty()) {
      pb.environment().put(AUTH_TOKEN_ENV, token);
    }
  }
}
