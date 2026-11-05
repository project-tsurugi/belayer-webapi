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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

public class CommandArgumentsTest {

  private static final String SESSION_SET =
      "tgctl_w session set :%s %s %s --monitor %s --conf %s";

  @Test
  public void eachValueStaysOneArgument() {
    var args = CommandArguments.build(SESSION_SET, "12", "sql.plan_recording",
        "a b; touch /tmp/x $(id) `id` | cat \"q\" 'q' > out", "/tmp/m.log", "/etc/tsurugi.ini");

    assertEquals(List.of("tgctl_w", "session", "set", ":12", "sql.plan_recording",
        "a b; touch /tmp/x $(id) `id` | cat \"q\" 'q' > out",
        "--monitor", "/tmp/m.log", "--conf", "/etc/tsurugi.ini"), args);
  }

  @Test
  public void placeholderInsideAWord() {
    var args = CommandArguments.build("stub --monitor=%s conf %s", "/tmp/m.log", "a b");

    assertEquals(List.of("stub", "--monitor=/tmp/m.log", "conf", "a b"), args);
  }

  @Test
  public void listValueBecomesSeveralArguments() {
    var args = CommandArguments.build("tgctl_w start --monitor %s --conf %s --mode %s",
        "/tmp/m.log", "/etc/tsurugi.ini", List.of("replica", "--replicate-from", "dns:///m:50051"));

    assertEquals(List.of("tgctl_w", "start", "--monitor", "/tmp/m.log", "--conf", "/etc/tsurugi.ini",
        "--mode", "replica", "--replicate-from", "dns:///m:50051"), args);
  }

  @Test
  public void valuesLeftOverAreIgnored() {
    var args = CommandArguments.build("stub --monitor=%s conf %s", "/tmp/m.log", "/etc/tsurugi.ini", "unused");

    assertEquals(List.of("stub", "--monitor=/tmp/m.log", "conf", "/etc/tsurugi.ini"), args);
  }

  @Test
  public void missingValuesAreRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> CommandArguments.build("tgctl_w shutdown --monitor %s --conf %s", "/tmp/m.log"));
  }

  @Test
  public void listForAPartOfAWordIsRejected() {
    assertThrows(IllegalArgumentException.class,
        () -> CommandArguments.build("stub --mode=%s", List.of("a", "b")));
  }

  @Test
  public void whitespaceOfTheTemplateIsNotKept() {
    var args = CommandArguments.build("  tgctl_w   shutdown\t--monitor %s  ", "/tmp/m.log");

    assertEquals(List.of("tgctl_w", "shutdown", "--monitor", "/tmp/m.log"), args);
  }

  @Test
  public void tokenGoesToTheEnvironment() {
    var pb = new ProcessBuilder("true");
    CommandArguments.setAuthToken(pb, "SECRET");

    assertEquals("SECRET", pb.environment().get(CommandArguments.AUTH_TOKEN_ENV));
    assertFalse(pb.command().contains("SECRET"));
  }

  @Test
  public void noTokenNoEnvironment() {
    var pb = new ProcessBuilder("true");
    pb.environment().remove(CommandArguments.AUTH_TOKEN_ENV);
    CommandArguments.setAuthToken(pb, null);
    CommandArguments.setAuthToken(pb, "");

    assertFalse(pb.environment().containsKey(CommandArguments.AUTH_TOKEN_ENV));
  }
}
