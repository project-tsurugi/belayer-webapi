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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.tsurugidb.belayer.webapi.config.RouterPath;
import com.tsurugidb.belayer.webapi.dto.DownloadPathList;

/**
 * Missing items in the requests of the file APIs are rejected with 400, not 500 (BW-5).
 */
@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "webapi.storage.root=./test_tmp_validation")
public class FileSystemApiRequestValidationTest {

  private static final String TEST_USER = "test_user";

  WebTestClient client;

  @Autowired
  RouterFunction<ServerResponse> routerFunction;

  @Value("${webapi.storage.root}")
  private String storageRootDir;

  @BeforeEach
  public void setUp() throws IOException {
    client = WebTestClient
        .bindToRouterFunction(routerFunction)
        .apply(SecurityMockServerConfigurers.springSecurity())
        .configureClient()
        .build();
    Files.createDirectories(userDir());
  }

  @AfterEach
  public void tearDown() throws IOException {
    try (var stream = Files.walk(Path.of(storageRootDir))) {
      stream.sorted(Comparator.reverseOrder())
          .map(Path::toFile)
          .forEach(File::delete);
    }
  }

  private Path userDir() {
    return Path.of(storageRootDir, TEST_USER);
  }

  private void writeFile(String path, String contents) throws IOException {
    Path file = userDir().resolve(path);
    Files.createDirectories(file.getParent());
    Files.write(file, contents.getBytes(StandardCharsets.US_ASCII));
  }

  private WebTestClient.ResponseSpec postJson(RouterPath path, String json) {
    return client.post().uri(path.getPath())
        .contentType(MediaType.APPLICATION_JSON)
        .bodyValue(json)
        .exchange();
  }

  private WebTestClient.ResponseSpec postNoBody(RouterPath path) {
    return client.post().uri(path.getPath())
        .contentType(MediaType.APPLICATION_JSON)
        .exchange();
  }

  // upload: "destDir" is optional

  private WebTestClient.ResponseSpec upload(String destDir) {
    MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
    bodyBuilder.part("file", "abcd".getBytes(StandardCharsets.US_ASCII)).header("Content-Disposition",
        "form-data; name=file; filename=test-file.txt");
    if (destDir != null) {
      bodyBuilder.part("destDir", destDir.getBytes(StandardCharsets.US_ASCII)).header("Content-Disposition",
          "form-data; name=destDir");
    }
    return client.post().uri(RouterPath.UPLOAD_API.getPath())
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
        .exchange();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testUpload_noDestDir() {
    upload(null)
        .expectStatus().isOk()
        .expectBody(DownloadPathList.class)
        .isEqualTo(new DownloadPathList(List.of("test-file.txt"), null));
    assertTrue(Files.exists(userDir().resolve("test-file.txt")));
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testUpload_emptyDestDir() {
    upload("")
        .expectStatus().isOk()
        .expectBody(DownloadPathList.class)
        .isEqualTo(new DownloadPathList(List.of("test-file.txt"), null));
    assertTrue(Files.exists(userDir().resolve("test-file.txt")));
  }

  // delete/file

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDeleteFile_noPath() {
    postJson(RouterPath.DELETE_FILE_API, "{}").expectStatus().isBadRequest();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDeleteFile_noBody() {
    postNoBody(RouterPath.DELETE_FILE_API).expectStatus().isBadRequest();
  }

  // delete/dir

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDeleteDir_noPath() {
    postJson(RouterPath.DELETE_DIR_API, "{\"force\": true}").expectStatus().isBadRequest();
    assertTrue(Files.isDirectory(userDir()));
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDeleteDir_noBody() {
    postNoBody(RouterPath.DELETE_DIR_API).expectStatus().isBadRequest();
  }

  // delete/files

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDeleteFiles_noPathList() {
    postJson(RouterPath.DELETE_FILES_API, "{}").expectStatus().isBadRequest();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDeleteFiles_nullInPathList() throws IOException {
    writeFile("a.txt", "a");
    postJson(RouterPath.DELETE_FILES_API, "{\"pathList\": [\"a.txt\", null]}").expectStatus().isBadRequest();
    // checked before anything is deleted
    assertTrue(Files.exists(userDir().resolve("a.txt")));
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDeleteFiles_emptyPathList() {
    postJson(RouterPath.DELETE_FILES_API, "{\"pathList\": []}").expectStatus().isOk();
  }

  // downloadzip

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDownloadZip_noBody() {
    postNoBody(RouterPath.DOWNLOADZIP_API).expectStatus().isBadRequest();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDownloadZip_noPathList() {
    postJson(RouterPath.DOWNLOADZIP_API, "{}").expectStatus().isBadRequest();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDownloadZip_nullInPathList() throws IOException {
    writeFile("a.txt", "a");
    postJson(RouterPath.DOWNLOADZIP_API, "{\"pathList\": [\"a.txt\", null]}").expectStatus().isBadRequest();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDownloadZip_emptyPathList() {
    postJson(RouterPath.DOWNLOADZIP_API, "{\"pathList\": []}").expectStatus().isBadRequest();
  }

  private List<String> zipEntries(byte[] bytes) {
    List<String> names = new ArrayList<>();
    try (var in = new ZipInputStream(new ByteArrayInputStream(bytes))) {
      for (var entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
        names.add(entry.getName());
      }
    } catch (IOException ex) {
      throw new RuntimeException(ex);
    }
    names.sort(Comparator.naturalOrder());
    return names;
  }

  private List<String> downloadZip(String json) {
    var body = postJson(RouterPath.DOWNLOADZIP_API, json)
        .expectStatus().isOk()
        .expectBody(byte[].class)
        .returnResult()
        .getResponseBody();
    return zipEntries(body);
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDownloadZip_filesDirectlyUnderStorage() throws IOException {
    // as WebAdmin sends the files listed directly under the storage area
    writeFile("a.txt", "a");
    writeFile("b.txt", "b");
    assertEquals(Arrays.asList("a.txt", "b.txt"), downloadZip("{\"pathList\": [\"a.txt\", \"b.txt\"]}"));
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDownloadZip_sameDirectoryWrittenDifferently() throws IOException {
    writeFile("a.txt", "a");
    writeFile("b.txt", "b");
    assertEquals(Arrays.asList("a.txt", "b.txt"), downloadZip("{\"pathList\": [\"./a.txt\", \"b.txt\"]}"));

    writeFile("x/c.txt", "c");
    writeFile("x/d.txt", "d");
    assertEquals(Arrays.asList("c.txt", "d.txt"), downloadZip("{\"pathList\": [\"./x/c.txt\", \"x/d.txt\"]}"));
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDownloadZip_differentDirectories() throws IOException {
    writeFile("a.txt", "a");
    writeFile("x/c.txt", "c");
    postJson(RouterPath.DOWNLOADZIP_API, "{\"pathList\": [\"a.txt\", \"x/c.txt\"]}").expectStatus().isBadRequest();
  }
}
