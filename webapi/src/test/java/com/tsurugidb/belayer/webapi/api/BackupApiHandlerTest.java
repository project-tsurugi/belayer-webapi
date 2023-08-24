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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.ArrayList;
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
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.tsurugidb.belayer.webapi.config.Router.ApiPath;
import com.tsurugidb.belayer.webapi.dto.BackupJob;
import com.tsurugidb.belayer.webapi.dto.Job;
import com.tsurugidb.belayer.webapi.dto.JobList;
import com.tsurugidb.belayer.webapi.dto.JobResult;
import com.tsurugidb.belayer.webapi.dto.RestoreJob;
import com.tsurugidb.belayer.webapi.service.BackupRestoreService;
import com.tsurugidb.belayer.webapi.service.FileSystemService;
import com.tsurugidb.belayer.webapi.service.JobIdService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "webapi.storage.root=./test_tmp")
public class BackupApiHandlerTest {

  WebTestClient client;

  @Autowired
  RouterFunction<ServerResponse> routerFunction;

  @MockBean
  JobIdService jobIdService;

  @MockBean
  BackupRestoreService backupService;

  @MockBean
  FileSystemService fileSystemService;

  @Value("${webapi.storage.root}")
  private String storageRootDir;

  private static final String TEST_USER = "test_user";

  @BeforeEach
  public void setUp() throws IOException {
    client = WebTestClient
        .bindToRouterFunction(routerFunction)
        .apply(SecurityMockServerConfigurers.springSecurity())
        .configureClient()
        .build();
  }

  @AfterEach
  public void tearDown() throws IOException {
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testRequestBackup() {

    String jobId = "TESTJOBID";

    var exp = new BackupJob();
    exp.setJobId(jobId);
    exp.setUid(TEST_USER);
    exp.setType("backup");

    when(jobIdService.createNewJobId()).thenReturn(jobId);
    when(backupService.startBackup(any())).thenReturn(Mono.just(exp));

    String url = ApiPath.BACKUP_START_API + "/{dirpath}";
    String dirPath = "path/to/savedir";

    client.post().uri(url, dirPath)
        .exchange()
        .expectStatus().isOk()
        .expectBody(JobResult.class)
        .isEqualTo(new JobResult(jobId, TEST_USER, "backup"));
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testShowBackupJobDetail() {

    String jobId = "TESTJOBID";
    BackupJob expectJobStatus = new BackupJob();
    expectJobStatus.setJobId(jobId);

    when(backupService.getJob(anyString(), anyString(), anyString()))
        .thenReturn(Mono.just(expectJobStatus));

    String url = ApiPath.BACKUP_STATUS_API + "/backup/{jobid}";

    client.get().uri(url, jobId)
        .exchange()
        .expectStatus().isOk()
        .expectBody(BackupJob.class)
        .isEqualTo(expectJobStatus);
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testListBackupJob() {

    List<Job> jobList = new ArrayList<>();
    jobList.add(new BackupJob());
    JobList expectedReturn = new JobList(jobList);

    when(backupService.getJobList(anyString(), anyString())).thenReturn(Flux.fromIterable(jobList));

    String url = ApiPath.LIST_BACKUP_STATUS_API + "/backup";

    client.get().uri(url)
        .exchange()
        .expectStatus().isOk()
        .expectBody(JobList.class)
        .isEqualTo(expectedReturn);
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testCancelBackupRestoreJob() {

    String jobId = "TESTJOBID";
    RestoreJob expectedReturn = new RestoreJob();
    expectedReturn.setJobId(jobId);

    when(backupService.cancelBackupRestoreJob(anyString(), anyString(), anyString()))
        .thenReturn(Mono.just(expectedReturn));

    String url = ApiPath.CANCEL_BACKUP_RESTORE_API + "/backup/{jobId}";

    client.post().uri(url, jobId)
        .exchange()
        .expectStatus().isOk()
        .expectBody(RestoreJob.class)
        .isEqualTo(expectedReturn);
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testRequestRestore() {

    String jobId = "TESTJOBID";
    var exp = new RestoreJob();
    exp.setJobId(jobId);
    exp.setUid(TEST_USER);
    exp.setType("restore");

    when(jobIdService.createNewJobId()).thenReturn(jobId);
    when(backupService.startRestore(any())).thenReturn(Mono.just(exp));

    doNothing().when(fileSystemService).checkDirPath(anyString(), anyString());
    doNothing().when(fileSystemService).checkFileExists(anyString(), anyString());

    String url = ApiPath.RESTORE_START_API + "/{zip_file_path}";
    String zipFilePath = "path/to/backup.zip";

    client.post().uri(url, zipFilePath)
        .exchange()
        .expectStatus().isOk()
        .expectBody(JobResult.class)
        .isEqualTo(new JobResult(jobId, TEST_USER, "restore"));
  }

}
