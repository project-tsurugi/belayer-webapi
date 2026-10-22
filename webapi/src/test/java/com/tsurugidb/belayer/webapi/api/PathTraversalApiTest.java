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
package com.tsurugidb.belayer.webapi.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.tsurugidb.belayer.webapi.config.RouterPath;
import com.tsurugidb.belayer.webapi.dto.BackupRestoreStartRequestBody;
import com.tsurugidb.belayer.webapi.dto.LoadJob;
import com.tsurugidb.belayer.webapi.dto.LoadRequestParam;
import com.tsurugidb.belayer.webapi.dto.RestoreJob;
import com.tsurugidb.belayer.webapi.service.BackupRestoreService;
import com.tsurugidb.belayer.webapi.service.DumpLoadService;
import com.tsurugidb.belayer.webapi.service.JobIdService;

import reactor.core.publisher.Mono;

/**
 * Paths given to the restore and load APIs must stay inside the user's storage
 * area (BW-1). The storage root is a relative path here, as in some setups.
 */
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "webapi.storage.root=./test_tmp_traversal")
public class PathTraversalApiTest {

  WebTestClient client;

  @Autowired
  RouterFunction<ServerResponse> routerFunction;

  @MockBean
  JobIdService jobIdService;

  @MockBean
  BackupRestoreService backupRestoreService;

  @MockBean
  DumpLoadService dumpLoadService;

  @Value("${webapi.storage.root}")
  private String storageRootDir;

  private static final String TEST_USER = "test_user";
  private static final String OTHER_USER = "other_user";
  private static final String JOB_ID = "TESTJOBID";

  @BeforeEach
  public void setUp() throws IOException {
    client = WebTestClient
        .bindToRouterFunction(routerFunction)
        .apply(SecurityMockServerConfigurers.springSecurity())
        .configureClient()
        .build();

    // a file of the user and a file of another user
    writeFile(Path.of(storageRootDir, TEST_USER, "mine", "b.zip"));
    writeFile(Path.of(storageRootDir, TEST_USER, "mine", "a.parquet"));
    writeFile(Path.of(storageRootDir, OTHER_USER, "b.zip"));
    writeFile(Path.of(storageRootDir, OTHER_USER, "secret.parquet"));

    when(jobIdService.createNewJobId()).thenReturn(JOB_ID);

    var restoreJob = new RestoreJob();
    restoreJob.setJobId(JOB_ID);
    restoreJob.setUid(TEST_USER);
    restoreJob.setType("restore");
    when(backupRestoreService.startRestore(any())).thenReturn(Mono.just(restoreJob));

    var loadJob = new LoadJob();
    loadJob.setJobId(JOB_ID);
    loadJob.setUid(TEST_USER);
    loadJob.setType("load");
    when(dumpLoadService.startLoad(any())).thenReturn(Mono.just(loadJob));
  }

  @AfterEach
  public void tearDown() throws IOException {
    try (var stream = Files.walk(Path.of(storageRootDir))) {
      stream.sorted(Comparator.reverseOrder())
          .map(Path::toFile)
          .forEach(File::delete);
    }
  }

  private void writeFile(Path path) throws IOException {
    Files.createDirectories(path.getParent());
    Files.write(path, "data".getBytes(StandardCharsets.US_ASCII));
  }

  private WebTestClient.ResponseSpec requestRestore(String zipFilePath) {
    var reqBody = new BackupRestoreStartRequestBody();
    reqBody.setZipFilePath(zipFilePath);
    return client.post().uri(RouterPath.RESTORE_START_API.getPath())
        .body(BodyInserters.fromValue(reqBody))
        .exchange();
  }

  private WebTestClient.ResponseSpec requestLoad(String filePath) {
    var reqBody = new LoadRequestParam();
    reqBody.setFiles(List.of(filePath));
    return client.post().uri(RouterPath.LOAD_START_API.getPath(), "tbl")
        .body(BodyInserters.fromValue(reqBody))
        .exchange();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testRestore_ok() {
    requestRestore("mine/b.zip").expectStatus().isOk();
    verify(backupRestoreService).startRestore(any());
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testRestore_absolutePath_staysInUserDir() {
    requestRestore("/mine/b.zip").expectStatus().isOk();
    verify(backupRestoreService).startRestore(any());
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testRestore_otherUsersFile() {
    requestRestore("../" + OTHER_USER + "/b.zip").expectStatus().isBadRequest();
    verify(backupRestoreService, never()).startRestore(any());
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testRestore_traversalBackIntoUserDir() {
    // leaves the user's area on the way, but ends inside it
    requestRestore("mine/../../" + TEST_USER + "/mine/b.zip").expectStatus().isOk();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testLoad_ok() {
    requestLoad("mine/a.parquet").expectStatus().isOk();
    verify(dumpLoadService).startLoad(any());
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testLoad_otherUsersFile() {
    requestLoad("../" + OTHER_USER + "/secret.parquet").expectStatus().isBadRequest();
    verify(dumpLoadService, never()).startLoad(any());
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testLoad_outsideStorageRoot() {
    requestLoad("../../build.gradle").expectStatus().isBadRequest();
    verify(dumpLoadService, never()).startLoad(any());
  }
}
