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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.tsurugidb.belayer.webapi.api.helper.UploadHelper;
import com.tsurugidb.belayer.webapi.config.Router.ApiPath;
import com.tsurugidb.belayer.webapi.dto.DumpLoadRequestParam;
import com.tsurugidb.belayer.webapi.dto.DumpResult;
import com.tsurugidb.belayer.webapi.dto.Job.JobStatus;
import com.tsurugidb.belayer.webapi.dto.LoadResult;
import com.tsurugidb.belayer.webapi.dto.TransactionStartBody;
import com.tsurugidb.belayer.webapi.dto.TransactionStatus;
import com.tsurugidb.belayer.webapi.model.SystemTime;
import com.tsurugidb.belayer.webapi.service.DumpLoadService;
import com.tsurugidb.belayer.webapi.service.FileSystemService;
import com.tsurugidb.belayer.webapi.service.JobIdService;
import com.tsurugidb.belayer.webapi.service.StatefulDumpLoadService;
import com.tsurugidb.belayer.webapi.service.tsubakuro.TsubakuroServiceStub;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@ActiveProfiles("ut")
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "webapi.storage.root=./test_tmp")
public class StatefulApiHandlerTest {

    WebTestClient client;

    @Autowired
    RouterFunction<ServerResponse> routerFunction;

    @MockBean
    JobIdService jobIdService;

    @MockBean
    SystemTime systemTime;

    @SpyBean
    StatefulDumpLoadService statefulDumploadService;

    @SpyBean
    DumpLoadService dumpLoadService;

    @MockBean
    FileSystemService fileSystemService;

    @MockBean
    TsubakuroServiceStub tsubakuroService;

    @MockBean
    UploadHelper uploadHelper;

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
    public void testTransactionSenario() {

        var jobId = "TESTJOBID";
        var mode = "read_write";
        var tableName = "FOO_TBL";

        var now = Instant.parse("2022-06-30T12:00:00.000Z");
        when(systemTime.now()).thenReturn(now);

        var status = new TransactionStatus();
        status.setTransactionId(jobId);
        status.setType(mode);
        status.setStatus(JobStatus.AVAILABLE.toString());
        status.setStartTime(now);

        when(jobIdService.createNewJobId()).thenReturn(jobId);
        when(tsubakuroService.createTransaction(any(), any(), any(), any(), anyBoolean(), any()))
                .thenCallRealMethod();

        client.post().uri(ApiPath.START_TRANSACTION_API)
                .body(BodyInserters.fromValue(new TransactionStartBody(mode, "10", null)))
                .exchange()
                .expectStatus().isOk()
                .expectBody(TransactionStatus.class)
                .isEqualTo(status);

        String url = ApiPath.SHOW_TRANSACTION_STATUS_API + "/{transactionid}";
        client.get().uri(url, jobId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TransactionStatus.class)
                .isEqualTo(status);

        var list = List.of("foo/aaa.parquet");
        var dumpResult = new DumpResult();
        dumpResult.setTransactionId(jobId);
        dumpResult.setTable(tableName);
        dumpResult.setFormat("parquet");
        dumpResult.setDownloadPathList(list);

        when(fileSystemService.convertToDownloadPath(any(), any())).thenReturn(Path.of("foo/aaa.parquet"));
        when(tsubakuroService.dumpTable(any())).thenReturn(Flux.fromIterable(List.of(Path.of("/aaa"))));

        url = ApiPath.STREAM_DUMP_API + "/{transactionid}/{table_name}";
        client.post().uri(url, jobId, tableName)
                // .body(BodyInserters.fromValue(new StreamDumpRequestBody("parquet",
                // "normal")))
                .exchange()
                .expectStatus().isOk()
                .expectBody(DumpResult.class)
                .isEqualTo(dumpResult);

        String fileName = "test-file.parquet";
        var loadResult = new LoadResult();
        loadResult.setTransactionId(jobId);
        loadResult.setTable(tableName);
        loadResult.setFormat(DumpLoadRequestParam.FORMAT_DETECT_BY_EXTENSION);
        loadResult.setDumpFiles(List.of(jobId + "/" + fileName));

        when(tsubakuroService.loadFile(any(), any())).thenReturn(Mono.just(jobId + "/" + fileName));
        when(uploadHelper.saveFile(anyString(), any(), any())).thenReturn(Mono.just(jobId + "/" + fileName));
        when(fileSystemService.convertToAbsolutePath(any(), any())).thenCallRealMethod();
        when(fileSystemService.convertToDownloadPath(any(), any())).thenReturn(Path.of("TESTJOBID/test-file.parquet"));
        doNothing().when(fileSystemService).deleteFile(anyString(), anyString());

        MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
        bodyBuilder.part("file", "abcd".getBytes(StandardCharsets.US_ASCII))
                .header("Content-Disposition", "form-data; name=file; filename=" + fileName);

        url = ApiPath.STREAM_LOAD_API + "/{transactionid}/{table_name}";
        client.post().uri(url, jobId, tableName)
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
                .exchange()
                .expectStatus().isOk()
                .expectBody(LoadResult.class)
                .isEqualTo(loadResult);

        status.setStatus(JobStatus.COMMITED.name());
        status.setEndTime(now);

        url = ApiPath.FINISH_TRANSACTION_API + "/commit/{transactionid}";
        client.post().uri(url, jobId)
                .exchange()
                .expectStatus().isOk()
                .expectBody(TransactionStatus.class)
                .isEqualTo(status);

    }

}