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

import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

import com.tsurugidb.belayer.webapi.api.helper.UploadHelper.UploadParameter;

/**
 * Objects that are written to the debug log must not include tokens in toString().
 */
public class TokenNotInToStringTest {

  private static final String TOKEN = "secret-token-value";

  private static void assertNoToken(Object target) {
    assertFalse(target.toString().contains(TOKEN), target.getClass().getSimpleName() + ": " + target);
  }

  @Test
  public void test_jobs() {
    var backup = new BackupJob();
    backup.setCredentials(TOKEN);
    assertNoToken(backup);

    var restore = new RestoreJob();
    restore.setCredentials(TOKEN);
    assertNoToken(restore);

    var dump = new DumpJob();
    dump.setCredentials(TOKEN);
    assertNoToken(dump);

    var load = new LoadJob();
    load.setCredentials(TOKEN);
    assertNoToken(load);

    var tx = new LongTransactionJob();
    tx.setCredentials(TOKEN);
    assertNoToken(tx);
  }

  @Test
  public void test_request_params() {
    var dump = new DumpRequestParam();
    dump.setCredentials(TOKEN);
    assertNoToken(dump);

    var load = new LoadRequestParam();
    load.setCredentials(TOKEN);
    assertNoToken(load);

    var backupRestore = new BackupRestoreRequestParam();
    backupRestore.setCredentials(TOKEN);
    assertNoToken(backupRestore);

    var tx = new TransactionApiParameter();
    tx.setCredentials(TOKEN);
    assertNoToken(tx);

    var upload = new UploadParameter();
    upload.setCredentials(TOKEN);
    assertNoToken(upload);

    var loadParam = new LoadParameter();
    loadParam.setCredentials(TOKEN);
    assertNoToken(loadParam);
  }

  @Test
  public void test_session_dtos() {
    var status = new SessionStatus();
    status.setToken(TOKEN);
    assertNoToken(status);

    var variable = new SessionVariable();
    variable.setToken(TOKEN);
    assertNoToken(variable);
  }

  @Test
  public void test_auth_result() {
    var result = new AuthResult();
    result.setAccessToken(TOKEN);
    result.setRefreshToken(TOKEN);
    assertNoToken(result);
  }
}
