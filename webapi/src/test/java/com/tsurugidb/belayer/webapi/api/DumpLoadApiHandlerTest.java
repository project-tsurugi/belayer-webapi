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
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.tsurugidb.belayer.webapi.config.RouterPath;
import com.tsurugidb.belayer.webapi.dto.ColumnMapping;
import com.tsurugidb.belayer.webapi.dto.DumpJob;
import com.tsurugidb.belayer.webapi.dto.DumpRequestParam;
import com.tsurugidb.belayer.webapi.dto.ErrorResult;
import com.tsurugidb.belayer.webapi.dto.Job;
import com.tsurugidb.belayer.webapi.dto.JobList;
import com.tsurugidb.belayer.webapi.dto.JobResult;
import com.tsurugidb.belayer.webapi.dto.LoadJob;
import com.tsurugidb.belayer.webapi.dto.LoadRequestParam;
import com.tsurugidb.belayer.webapi.service.DumpLoadService;
import com.tsurugidb.belayer.webapi.service.FileSystemService;
import com.tsurugidb.belayer.webapi.service.JobIdService;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "webapi.storage.root=./test_tmp")
public class DumpLoadApiHandlerTest {

  WebTestClient client;

  @Autowired
  RouterFunction<ServerResponse> routerFunction;

  @MockBean
  JobIdService jobIdService;

  @MockBean
  DumpLoadService dumpLoadService;

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
  public void testRequestDump() {

    String jobId = "TESTJOBID";
    String table = "FOO_TBL";

    var exp = new DumpJob();
    exp.setJobId(jobId);
    exp.setUid(TEST_USER);
    exp.setType("dump");
    exp.setTable(table);

    when(jobIdService.createNewJobId()).thenReturn(jobId);
    when(dumpLoadService.startDump(any())).thenReturn(Mono.just(exp));

    String url = RouterPath.DUMP_START_API.getPath();
    String dirPath = "path/to/savedir";
    var reqBody = new DumpRequestParam();
    reqBody.setDirPath(dirPath);

    client.post().uri(url, table)
        .body(BodyInserters.fromValue(reqBody))
        .exchange()
        .expectStatus().isOk()
        .expectBody(JobResult.class)
        .isEqualTo(new JobResult(jobId, TEST_USER, "dump"));
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testRequestLoad() {

    String jobId = "TESTJOBID";
    String table = "FOO_TBL";

    var exp = new LoadJob();
    exp.setJobId(jobId);
    exp.setUid(TEST_USER);
    exp.setType("load");
    exp.setTable(table);

    var reqBody = new LoadRequestParam();
    reqBody.setFiles(List.of("test.parquet"));
    reqBody.setMappings(List.of(new ColumnMapping("abc", "abc")));

    when(jobIdService.createNewJobId()).thenReturn(jobId);
    when(dumpLoadService.startLoad(any())).thenReturn(Mono.just(exp));

    String url = "/api/load/{table}";
    client.post().uri(url, table)
    .body(BodyInserters.fromValue(reqBody))
    .exchange()
        .expectStatus().isOk()
        .expectBody(JobResult.class)
        .isEqualTo(new JobResult(jobId, TEST_USER, "load"));
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testRequestLoad_fail() {

    String jobId = "TESTJOBID";
    String table = "FOO_TBL";

    var exp = new LoadJob();
    exp.setJobId(jobId);
    exp.setUid(TEST_USER);
    exp.setType("load");
    exp.setTable(table);

    when(jobIdService.createNewJobId()).thenReturn(jobId);
    when(dumpLoadService.startLoad(any())).thenReturn(Mono.just(exp));

    String url = RouterPath.LOAD_START_API.getPath();
    client.post().uri(url, table)
        .accept(MediaType.APPLICATION_JSON)
        // no body
        .exchange()
        .expectStatus().isBadRequest()
        .expectBody(ErrorResult.class)
        .isEqualTo(new ErrorResult("no paramters in request body"));
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testRequestLoad_invalid_mapping() {

    String table = "FOO_TBL";

    var reqBody = new LoadRequestParam();
    reqBody.setFiles(List.of("test.parquet"));
    reqBody.setMappings(List.of(new ColumnMapping("@a", "abc")));


    String url = RouterPath.LOAD_START_API.getPath();
    client.post().uri(url, table)
        .accept(MediaType.APPLICATION_JSON)
        .body(BodyInserters.fromValue(reqBody))
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testRequestLoad_invalid_mapping2() {

    String table = "FOO_TBL";

    var reqBody = new LoadRequestParam();
    reqBody.setFiles(List.of("test.parquet"));
    reqBody.setMappings(List.of(new ColumnMapping("abc", "@abc")));


    String url = RouterPath.LOAD_START_API.getPath();
    client.post().uri(url, table)
        .accept(MediaType.APPLICATION_JSON)
        .body(BodyInserters.fromValue(reqBody))
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testShowJobDetail() {

    String jobId = "TESTJOBID";
    var expectJobStatus = new DumpJob();
    expectJobStatus.setJobId(jobId);

    when(dumpLoadService.getJob(anyString(), anyString(), anyString()))
        .thenReturn(Mono.just(expectJobStatus));

    String url = RouterPath.DUMP_LOAD_STATUS_API.getPath();

    client.get().uri(url, "dump", jobId)
        .exchange()
        .expectStatus().isOk()
        .expectBody(DumpJob.class)
        .isEqualTo(expectJobStatus);
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testLisJob() {

    List<Job> jobList = new ArrayList<>();
    jobList.add(new LoadJob());
    JobList expectedReturn = new JobList(jobList);

    when(dumpLoadService.getJobList(anyString(), anyString())).thenReturn(Flux.fromIterable(jobList));

    String url = RouterPath.LIST_DUMP_LOAD_STATUS_API.getPath();

    client.get().uri(url, "load")
        .exchange()
        .expectStatus().isOk()
        .expectBody(JobList.class)
        .isEqualTo(expectedReturn);
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testCancelJob() {

    String jobId = "TESTJOBID";
    var expectedReturn = new LoadJob();
    expectedReturn.setJobId(jobId);

    when(dumpLoadService.cancelJob(anyString(), anyString(), anyString()))
        .thenReturn(Mono.just(expectedReturn));

    String url = RouterPath.CANCEL_DUMP_LOAD_API.getPath();

    client.post().uri(url, "load", jobId)
        .exchange()
        .expectStatus().isOk()
        .expectBody(LoadJob.class)
        .isEqualTo(expectedReturn);
  }

}
