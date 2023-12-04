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
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.util.UriBuilder;

import com.tsurugidb.belayer.webapi.config.Router.ApiPath;
import com.tsurugidb.belayer.webapi.dto.DeleteTarget;
import com.tsurugidb.belayer.webapi.dto.DownloadPathList;
import com.tsurugidb.belayer.webapi.dto.DownloadZip;
import com.tsurugidb.belayer.webapi.model.SystemTime;

@AutoConfigureWebTestClient
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = { "webapi.storage.root=./test_tmp",
    "webapi.list.max.files=20", "logging.level.root=INFO", "logging.level.com.tsurugidb=DEBUG",
    "logging.level.org.springframework=WARN" })
public class FileSystemApiHandlerTest {

  WebTestClient client;

  @Autowired
  RouterFunction<ServerResponse> routerFunction;

  @Value("${webapi.storage.root}")
  private String storageRootDir;

  @Value("${webapi.list.max.files}")
  private int listFileMaxSize;

  private static final String TEST_USER = "test_user";

  @MockBean
  SystemTime systemTime;

  @BeforeEach
  public void setUp() throws IOException {

    client = WebTestClient
        .bindToRouterFunction(routerFunction)
        .apply(springSecurity())
        .configureClient()
        .build();

    // create dir for test
    Files.createDirectories(Path.of(storageRootDir, TEST_USER));
  }

  @AfterEach
  public void tearDown() throws IOException {
    // delete dir for test
    Files.walk(Path.of(storageRootDir))
        .sorted(Comparator.reverseOrder())
        .map(Path::toFile)
        .forEach(File::delete);
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testUploadFiles() {

    String destDir = "dir_for_test";
    String fileName = "test-file.txt";

    MultipartBodyBuilder bodyBuilder = new MultipartBodyBuilder();
    bodyBuilder.part("file", "abcd".getBytes(StandardCharsets.US_ASCII)).header("Content-Disposition",
        "form-data; name=file; filename=" + fileName);
    bodyBuilder.part("destDir", destDir.getBytes(StandardCharsets.US_ASCII)).header("Content-Disposition",
        "form-data; name=destDir");
    bodyBuilder.part("overwrite", "true".getBytes(StandardCharsets.US_ASCII)).header("Content-Disposition",
        "form-data; name=overwrite");

    String[] expectFileNames = { destDir + "/" + fileName };

    client.post().uri(ApiPath.UPLOAD_API)
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .body(BodyInserters.fromMultipartData(bodyBuilder.build()))
        .exchange()
        .expectStatus().isOk()
        .expectBody(DownloadPathList.class)
        .isEqualTo(new DownloadPathList(Arrays.asList(expectFileNames), null));
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDownloadFile_invalid_dir() throws IOException {

    String destDir = "dir_for_test";
    String fileName = "test-file-to-download.txt";
    String filePath = "../" + destDir + "/" + fileName;

    client.get().uri("/api/download/{path}", filePath)
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDownloadFile_invalid_file() throws IOException {

    String destDir = "dir_for_test";
    String fileName = "test-file-to-download.txt";
    String filePath = destDir + "/" + fileName;

    Path dir = Path.of(storageRootDir, TEST_USER, destDir);
    Files.createDirectories(dir);

    client.get().uri("/api/download/{path}", filePath)
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDownloadFile_file() throws IOException {

    String destDir = "dir_for_test";
    String fileName = "test-file-to-download.txt";
    String filePath = destDir + "/" + fileName;
    String contents = "this is test file";

    Path dir = Path.of(storageRootDir, TEST_USER, destDir);
    Files.createDirectories(dir);
    Files.write(Path.of(dir.toString(), fileName), contents.getBytes(StandardCharsets.US_ASCII));

    var fileContents = contents.getBytes(StandardCharsets.US_ASCII);

    client.get().uri("/api/download/{path}", filePath)
        .exchange()
        .expectStatus().isOk()
        .expectBody(byte[].class)
        .isEqualTo(fileContents);
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDownloadFile_dir_as_zip() throws IOException {

    String destDir = "dir_for_test";
    String fileName = "test-file-to-download.txt";
    String contents = "this is test file";

    Path dir = Path.of(storageRootDir, TEST_USER, destDir);
    Files.createDirectories(dir);
    Files.write(Path.of(dir.toString(), fileName), contents.getBytes(StandardCharsets.US_ASCII));

    client.get().uri("/api/download/{path}", destDir)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType("application/zip")
        .expectHeader().contentDisposition(ContentDisposition.parse("attachment; filename=\"" + destDir + ".zip\""))
        .expectBody(byte[].class)
        .consumeWith(body -> {
          var bytes = body.getResponseBodyContent();
          try (var in = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry = in.getNextEntry();
            assertEquals(fileName, entry.getName());
          } catch (Exception e) {
            throw new RuntimeException("ex", e);
          }
        });
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDownloadFile_invalid_parquet_file() throws IOException {

    String destDir = "dir_for_test";
    String fileName = "test-file-to-download.txt";
    String filePath = destDir + "/" + fileName;

    Path dir = Path.of(storageRootDir, TEST_USER, destDir);
    Files.createDirectories(dir);
    Files.write(Path.of(dir.toString(), fileName), "dummy text".getBytes(StandardCharsets.US_ASCII));

    Function<UriBuilder, URI> uri = (builder -> builder.path("/api/download/{path}").queryParam("csv", "true")
        .build(filePath));

    client.get().uri(uri)
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDownloadZipFile_parquet_file() throws IOException {
    String testParquetFile1 = "./src/test/files/parquet/test.parquet";
    String testParquetFile2 = "./src/test/files/parquet/test2.parquet";

    String destDir = "dir_for_test";
    String fileName = "test.parquet";
    String fileName2 = "test2.parquet";

    Path dir = Path.of(storageRootDir, TEST_USER, destDir);

    try {
      Files.createDirectories(dir);
      Files.copy(Path.of(testParquetFile1), Path.of(dir.toString(), fileName));
      Files.copy(Path.of(testParquetFile2), Path.of(dir.toString(), fileName2));
    } catch (Exception ex) {
      System.out.println(ex.toString());
    }
    Function<UriBuilder, URI> uri = (builder -> builder.path(ApiPath.DOWNLOADZIP_API)
        // .queryParam("csv", "true")
        .build());

    var param = new DownloadZip();
    param.setPathList(
      new String[] {
        destDir + "/" + fileName,
        destDir + "/" + fileName2,
      }
    );

    var strDateTime = "2022-06-30T12:12:34.567Z";
    var now = Instant.parse(strDateTime);
    Mockito.when(systemTime.now()).thenReturn(now);

    var formattedString = DateTimeFormatter
      .ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.ofInstant(now, ZoneOffset.UTC));

    client.post().uri(uri)
        .bodyValue(param)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType("application/zip")
        .expectHeader().contentDisposition(ContentDisposition.parse("attachment; filename=\"" + "belayer_download_" + formattedString + ".zip\""))
        .expectBody(byte[].class)
        .consumeWith(body -> {
          var bytes = body.getResponseBodyContent();
          try (var in = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry = in.getNextEntry();
            assertEquals(fileName, entry.getName());
            entry = in.getNextEntry();
            assertEquals(fileName2, entry.getName());
          } catch (Exception e) {
            throw new RuntimeException("ex", e);
          }
        });
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDownloadZipFile_to_csv_file() throws IOException {
    String testParquetFile1 = "./src/test/files/parquet/test.parquet";
    String testParquetFile2 = "./src/test/files/parquet/test2.parquet";

    String destDir = "dir_for_test";
    String fileName = "test.parquet";
    String fileName2 = "test2.parquet";

    String fileName_conv = "test.csv";
    String fileName2_conv = "test2.csv";

    Path dir = Path.of(storageRootDir, TEST_USER, destDir);

    try {
      Files.createDirectories(dir);
      Files.copy(Path.of(testParquetFile1), Path.of(dir.toString(), fileName));
      Files.copy(Path.of(testParquetFile2), Path.of(dir.toString(), fileName2));
    } catch (Exception ex) {
      System.out.println(ex.toString());
    }
    Function<UriBuilder, URI> uri = (builder -> builder.path(ApiPath.DOWNLOADZIP_API)
        .queryParam("csv", "true")
        .build());

    var param = new DownloadZip();
    param.setPathList(
      new String[] {
        destDir + "/" + fileName,
        destDir + "/" + fileName2,
      }
    );

    var strDateTime = "2022-06-30T12:12:34.567Z";
    var now = Instant.parse(strDateTime);
    Mockito.when(systemTime.now()).thenReturn(now);

    var formattedString = DateTimeFormatter
      .ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.ofInstant(now, ZoneOffset.UTC));

    client.post().uri(uri)
        .bodyValue(param)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType("application/zip")
        .expectHeader().contentDisposition(ContentDisposition.parse("attachment; filename=\"" + "belayer_download_" + formattedString + ".zip\""))
        .expectBody(byte[].class)
        .consumeWith(body -> {
          var bytes = body.getResponseBodyContent();
          try (var in = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry = in.getNextEntry();
            assertEquals(fileName_conv, entry.getName());
            entry = in.getNextEntry();
            assertEquals(fileName2_conv, entry.getName());
          } catch (Exception e) {
            throw new RuntimeException("ex", e);
          }
        });
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDownloadZipFile_csv_parquet_file() throws IOException {
    String testParquetFile1 = "./src/test/files/parquet/test.parquet";
    String testCsvFile2 = "./src/test/files/parquet/type.csv";

    String destDir = "dir_for_test";
    String fileName = "test.parquet";
    String fileName2 = "type.csv";

    Path dir = Path.of(storageRootDir, TEST_USER, destDir);

    try {
      Files.createDirectories(dir);
      Files.copy(Path.of(testParquetFile1), Path.of(dir.toString(), fileName));
      Files.copy(Path.of(testCsvFile2), Path.of(dir.toString(), fileName2));
    } catch (Exception ex) {
      System.out.println(ex.toString());
    }
    Function<UriBuilder, URI> uri = (builder -> builder.path(ApiPath.DOWNLOADZIP_API)
        // .queryParam("csv", "true")
        .build());

    var param = new DownloadZip();
    param.setPathList(
      new String[] {
        destDir + "/" + fileName,
        destDir + "/" + fileName2,
      }
    );

    var strDateTime = "2022-06-30T12:12:34.567Z";
    var now = Instant.parse(strDateTime);
    Mockito.when(systemTime.now()).thenReturn(now);

    var formattedString = DateTimeFormatter
      .ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.ofInstant(now, ZoneOffset.UTC));

    client.post().uri(uri)
        .bodyValue(param)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType("application/zip")
        .expectHeader().contentDisposition(ContentDisposition.parse("attachment; filename=\"" + "belayer_download_" + formattedString + ".zip\""))
        .expectBody(byte[].class)
        .consumeWith(body -> {
          var bytes = body.getResponseBodyContent();
          try (var in = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry = in.getNextEntry();
            assertEquals(fileName, entry.getName());
            entry = in.getNextEntry();
            assertEquals(fileName2, entry.getName());
          } catch (Exception e) {
            throw new RuntimeException("ex", e);
          }
        });
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDownloadZipFile_csv_parquet_conv_file() throws IOException {
    String testParquetFile1 = "./src/test/files/parquet/test.parquet";
    String testCsvFile2 = "./src/test/files/parquet/type.csv";

    String destDir = "dir_for_test";
    String fileName = "test.parquet";
    String fileName2 = "type.csv";

    String fileName_comv = "test.csv";

    Path dir = Path.of(storageRootDir, TEST_USER, destDir);

    try {
      Files.createDirectories(dir);
      Files.copy(Path.of(testParquetFile1), Path.of(dir.toString(), fileName));
      Files.copy(Path.of(testCsvFile2), Path.of(dir.toString(), fileName2));
    } catch (Exception ex) {
      System.out.println(ex.toString());
    }
    Function<UriBuilder, URI> uri = (builder -> builder.path(ApiPath.DOWNLOADZIP_API)
        .queryParam("csv", "true")
        .build());

    var param = new DownloadZip();
    param.setPathList(
      new String[] {
        destDir + "/" + fileName,
        destDir + "/" + fileName2,
      }
    );

    var strDateTime = "2022-06-30T12:12:34.567Z";
    var now = Instant.parse(strDateTime);
    Mockito.when(systemTime.now()).thenReturn(now);

    var formattedString = DateTimeFormatter
      .ofPattern("yyyyMMddHHmmssSSS").format(LocalDateTime.ofInstant(now, ZoneOffset.UTC));

    client.post().uri(uri)
        .bodyValue(param)
        .exchange()
        .expectStatus().isOk()
        .expectHeader().contentType("application/zip")
        .expectHeader().contentDisposition(ContentDisposition.parse("attachment; filename=\"" + "belayer_download_" + formattedString + ".zip\""))
        .expectBody(byte[].class)
        .consumeWith(body -> {
          var bytes = body.getResponseBodyContent();
          try (var in = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry = in.getNextEntry();
            assertEquals(fileName_comv, entry.getName());
            entry = in.getNextEntry();
            assertEquals(fileName2, entry.getName());
          } catch (Exception e) {
            throw new RuntimeException("ex", e);
          }
        });
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDownloadZipFile_invalid_path() throws IOException {

    String destDir = "dir_for_test";
    String destDir2 = "dir_for_test2";
    String fileName = "test.parquet";
    String fileName2 = "test2.parquet";

    Function<UriBuilder, URI> uri = (builder -> builder.path(ApiPath.DOWNLOADZIP_API)
        .build());

    var param = new DownloadZip();
    param.setPathList(
      new String[] {
        destDir + "/" + fileName,
        destDir2 + "/" + fileName2,
      }
    );

    var strDateTime = "2022-06-30T12:12:34.567Z";
    var now = Instant.parse(strDateTime);
    Mockito.when(systemTime.now()).thenReturn(now);

    client.post().uri(uri)
        .bodyValue(param)
        .exchange()
        .expectStatus().isBadRequest();
  }


  @Test
  @WithMockUser(username = TEST_USER)
  public void testDownloadZipFile_empty_path() throws IOException {

    Function<UriBuilder, URI> uri = (builder -> builder.path(ApiPath.DOWNLOADZIP_API)
        .build());

    var param = new DownloadZip();
    param.setPathList(
      new String[] {}
    );

    var strDateTime = "2022-06-30T12:12:34.567Z";
    var now = Instant.parse(strDateTime);
    Mockito.when(systemTime.now()).thenReturn(now);

    client.post().uri(uri)
        .bodyValue(param)
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDownloadZipFile_invalid_file() throws IOException {

    String destDir = "dir_for_test";
    String fileName = "test.parquet";
    String fileName2 = "test2.parquet";

    Function<UriBuilder, URI> uri = (builder -> builder.path(ApiPath.DOWNLOADZIP_API)
        .build());

    var param = new DownloadZip();
    param.setPathList(
      new String[] {
        destDir + "/" + fileName,
        destDir + "/" + fileName2,
      }
    );

    var strDateTime = "2022-06-30T12:12:34.567Z";
    var now = Instant.parse(strDateTime);
    Mockito.when(systemTime.now()).thenReturn(now);

    client.post().uri(uri)
        .bodyValue(param)
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testListFilesFileOnly() throws IOException {

    String destDir = "dir_for_test";
    String fileName1 = "test1.txt";
    String fileName2 = "testA.txt";
    String fileName3 = "test3.txt";
    // String filePath =
    byte[] contents = "this is test file".getBytes(StandardCharsets.US_ASCII);

    String[] expectFileNames = {
        destDir + "/" + fileName1,
        destDir + "/" + fileName2,
        destDir + "/" + fileName3
    };

    List<String> fileList = Arrays.asList(expectFileNames);
    fileList.sort(Comparator.naturalOrder());

    Path dir = Path.of(storageRootDir, TEST_USER, destDir);
    Files.createDirectories(dir);
    Files.write(Path.of(dir.toString(), fileName1), contents);
    Files.write(Path.of(dir.toString(), fileName2), contents);
    Files.write(Path.of(dir.toString(), fileName3), contents);

    String url = ApiPath.LIST_FILES_API + "/{path}";
    Function<UriBuilder, URI> uriBuilder = (builder -> builder.path(url).queryParam("hide_dir", "true")
        .build(destDir));

    client.get().uri(uriBuilder)
        .exchange()
        .expectStatus().isOk()
        .expectBody(DownloadPathList.class)
        .isEqualTo(new DownloadPathList(fileList, null));
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testListFilesDirOnly() throws IOException {

    String destDir = "dir_for_test";
    String fileName1 = "test1.txt";
    String fileName2 = "testA.txt";
    String fileName3 = "test3.txt";
    // String filePath =
    byte[] contents = "this is test file".getBytes(StandardCharsets.US_ASCII);

    String[] expectFileNames = {
        destDir + "/"
    };

    List<String> fileList = Arrays.asList(expectFileNames);
    fileList.sort(Comparator.naturalOrder());

    Path dir = Path.of(storageRootDir, TEST_USER, destDir);
    Files.createDirectories(dir);
    Files.write(Path.of(dir.toString(), fileName1), contents);
    Files.write(Path.of(dir.toString(), fileName2), contents);
    Files.write(Path.of(dir.toString(), fileName3), contents);

    String url = ApiPath.LIST_FILES_API + "/{path}";
    Function<UriBuilder, URI> uriBuilder = (builder -> builder.path(url).queryParam("hide_file", "true")
        .build(destDir));

    client.get().uri(uriBuilder)
        .exchange()
        .expectStatus().isOk()
        .expectBody(DownloadPathList.class)
        .isEqualTo(new DownloadPathList(fileList, null));
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testListFilesDirsAndFiles() throws IOException {

    String destDir = "dir_for_test";
    String fileName1 = "test1.txt";
    String fileName2 = "testA.txt";
    String fileName3 = "test3.txt";
    // String filePath =
    byte[] contents = "this is test file".getBytes(StandardCharsets.US_ASCII);

    String[] expectFileNames = {
        destDir + "/",
        destDir + "/" + fileName1,
        destDir + "/" + fileName2,
        destDir + "/" + fileName3,
    };

    List<String> fileList = Arrays.asList(expectFileNames);
    fileList.sort(Comparator.naturalOrder());

    Path dir = Path.of(storageRootDir, TEST_USER, destDir);
    Files.createDirectories(dir);
    Files.write(Path.of(dir.toString(), fileName1), contents);
    Files.write(Path.of(dir.toString(), fileName2), contents);
    Files.write(Path.of(dir.toString(), fileName3), contents);

    String url = ApiPath.LIST_FILES_API + "/{path}";
    Function<UriBuilder, URI> uriBuilder = (builder -> builder.path(url).build(destDir));

    client.get().uri(uriBuilder)
        .exchange()
        .expectStatus().isOk()
        .expectBody(DownloadPathList.class)
        .isEqualTo(new DownloadPathList(fileList, null));
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testListFilesDirsAndFiles_overlimit() throws IOException {

    String destDir = "dir_for_test";
    // String filePath =
    byte[] contents = "this is test file".getBytes(StandardCharsets.US_ASCII);

    List<String> fileList = new ArrayList<>();
    for (int i = 0; i < listFileMaxSize; i++) {
      fileList.add(Path.of(destDir + "/", String.format("test%03d.txt", i)).toString());
    }
    fileList.sort(Comparator.naturalOrder());

    int testSize = listFileMaxSize + 10;
    Path dir = Path.of(storageRootDir, TEST_USER, destDir);
    Files.createDirectories(dir);
    for (int i = 0; i < testSize; i++) {
      Files.write(Path.of(dir.toString(), String.format("test%03d.txt", i)), contents);
    }

    String url = ApiPath.LIST_FILES_API + "/{path}";
    Function<UriBuilder, URI> uriBuilder = (builder -> builder.path(url).queryParam("hide_dir", "true")
        .build(destDir));

    var message = "List size is over limit. size=" + testSize;

    client.get().uri(uriBuilder)
        .exchange()
        .expectStatus().isOk()
        .expectBody(DownloadPathList.class)
        .isEqualTo(new DownloadPathList(fileList, message));
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testListFilesInvalidQueryParams() throws IOException {

    String destDir = "dir_for_test";
    String fileName1 = "test1.txt";
    // String filePath =
    byte[] contents = "this is test file".getBytes(StandardCharsets.US_ASCII);

    String[] expectFileNames = {
        destDir + "/",
        destDir + "/" + fileName1,
    };

    List<String> fileList = Arrays.asList(expectFileNames);
    fileList.sort(Comparator.naturalOrder());

    Path dir = Path.of(storageRootDir, TEST_USER, destDir);
    Files.createDirectories(dir);
    Files.write(Path.of(dir.toString(), fileName1), contents);

    String url = ApiPath.LIST_FILES_API + "/{path}";
    Function<UriBuilder, URI> uriBuilder = (builder -> builder.path(url).queryParam("hide_file", "true")
        .queryParam("hide_dir", "true").build(destDir));

    client.get().uri(uriBuilder)
        .exchange()
        .expectStatus().isBadRequest();

  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDeleteFile_success() throws IOException {

    String destDir = "dir_for_test";
    String fileName1 = "test1.txt";
    // String filePath =
    byte[] contents = "this is test file".getBytes(StandardCharsets.US_ASCII);

    Path dir = Path.of(storageRootDir, TEST_USER, destDir);
    Files.createDirectories(dir);
    Files.write(Path.of(dir.toString(), fileName1), contents);

    String deleteFile = destDir + "/" + fileName1;

    var param = new DeleteTarget();
    param.setPath(deleteFile);

    client.post().uri(ApiPath.DELETE_FILE_API)
        .bodyValue(param)
        .exchange()
        .expectStatus().isOk()
        .expectBody(DeleteTarget.class)
        .isEqualTo(new DeleteTarget(TEST_USER, null, new String[] { deleteFile }, false));
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDeleteFiles_success() throws IOException {

    String destDir = "dir_for_test";
    String fileName1 = "test1.txt";
    String fileName2 = "test2.txt";
    byte[] contents = "this is test file".getBytes(StandardCharsets.US_ASCII);

    Path dir = Path.of(storageRootDir, TEST_USER, destDir);
    Files.createDirectories(dir);
    Files.write(Path.of(dir.toString(), fileName1), contents);
    Files.write(Path.of(dir.toString(), fileName2), contents);

    String deleteFile1 = destDir + "/" + fileName1;
    String deleteFile2 = destDir + "/" + fileName2;
    String[] files = new String[] { deleteFile1, deleteFile2 };
    var param = new DeleteTarget();
    param.setPathList(files);

    String url = ApiPath.DELETE_FILES_API;
    Function<UriBuilder, URI> uriBuilder = (builder -> builder.path(url)
        .build());

    client.post().uri(uriBuilder)
        .bodyValue(param)
        .exchange()
        .expectStatus().isOk()
        .expectBody(DeleteTarget.class)
        .isEqualTo(new DeleteTarget(TEST_USER, null, files, false));
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDeleteFile_not_found() throws IOException {

    String destDir = "dir_for_test";
    String fileName1 = "test1.txt";
    // String filePath =
    byte[] contents = "this is test file".getBytes(StandardCharsets.US_ASCII);

    Path dir = Path.of(storageRootDir, TEST_USER, destDir);
    Files.createDirectories(dir);
    Files.write(Path.of(dir.toString(), fileName1), contents);

    String deleteFile = destDir + "/invalid.txt";

    String url = ApiPath.DELETE_FILE_API + "/{path}";
    Function<UriBuilder, URI> uriBuilder = (builder -> builder.path(url)
        .build(deleteFile));

    client.post().uri(uriBuilder)
        .exchange()
        .expectStatus().isNotFound();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDeleteFile_file_is_dir() throws IOException {

    String destDir = "dir_for_test";

    Path dir = Path.of(storageRootDir, TEST_USER, destDir);
    Files.createDirectories(dir);

    String url = ApiPath.DELETE_FILE_API + "/{path}";
    Function<UriBuilder, URI> uriBuilder = (builder -> builder.path(url)
        .build(destDir));

    client.post().uri(uriBuilder)
        .exchange()
        .expectStatus().isNotFound();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDeleteFile_file_dir_traversal() throws IOException {

    String destDir = "../aaa/test1.txt";

    var param = new DeleteTarget();
    param.setPath(destDir);

    client.post().uri(ApiPath.DELETE_FILE_API)
        .bodyValue(param)
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDeleteDir_success() throws IOException {

    String destDir = "dir_for_test";

    Path dir = Path.of(storageRootDir, TEST_USER, destDir);
    Files.createDirectories(dir);

    var param = new DeleteTarget();
    param.setPath(destDir);

    client.post().uri(ApiPath.DELETE_DIR_API)
        .bodyValue(param)
        .exchange()
        .expectStatus().isOk()
        .expectBody(DeleteTarget.class)
        .isEqualTo(new DeleteTarget(TEST_USER, destDir, null, false));
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDeleteDir_not_empty() throws IOException {

    String destDir = "dir_for_test";
    String fileName1 = "test1.txt";
    // String filePath =
    byte[] contents = "this is test file".getBytes(StandardCharsets.US_ASCII);

    Path dir = Path.of(storageRootDir, TEST_USER, destDir);
    Files.createDirectories(dir);
    Files.write(Path.of(dir.toString(), fileName1), contents);

    var param = new DeleteTarget();
    param.setPath(destDir);

    client.post().uri(ApiPath.DELETE_DIR_API)
        .bodyValue(param)
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDeleteDir_not_found() throws IOException {

    String destDir = "dir_for_test";

    String url = ApiPath.DELETE_DIR_API + "/{path}";
    Function<UriBuilder, URI> uriBuilder = (builder -> builder.path(url)
        .build(destDir));

    client.post().uri(uriBuilder)
        .exchange()
        .expectStatus().isNotFound();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDeleteDir__dir_traversal() throws IOException {

    String destDir = "../aaa";

    var param = new DeleteTarget();
    param.setPath(destDir);

    client.post().uri(ApiPath.DELETE_DIR_API)
        .bodyValue(param)
        .exchange()
        .expectStatus().isBadRequest();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDeleteDir_dir_is_file() throws IOException {

    String destDir = "dir_for_test";
    String fileName1 = "test1.txt";
    // String filePath =
    byte[] contents = "this is test file".getBytes(StandardCharsets.US_ASCII);

    Path dir = Path.of(storageRootDir, TEST_USER, destDir);
    Files.createDirectories(dir);
    Files.write(Path.of(dir.toString(), fileName1), contents);

    String url = ApiPath.DELETE_DIR_API + "/{path}";
    Function<UriBuilder, URI> uriBuilder = (builder -> builder.path(url)
        .build(destDir + "/" + fileName1));

    client.post().uri(uriBuilder)
        .exchange()
        .expectStatus().isNotFound();
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDeleteDir_force() throws IOException {

    String destDir = "dir_for_test";
    String fileName1 = "test1.txt";
    // String filePath =
    byte[] contents = "this is test file".getBytes(StandardCharsets.US_ASCII);

    Path dir = Path.of(storageRootDir, TEST_USER, destDir);
    Files.createDirectories(dir);
    Files.write(Path.of(dir.toString(), fileName1), contents);

    var param = new DeleteTarget();
    param.setPath(destDir);
    param.setForce(true);

    client.post().uri(ApiPath.DELETE_DIR_API)
        .bodyValue(param)
        .exchange()
        .expectStatus().isOk()
        .expectBody(DeleteTarget.class)
        .isEqualTo(new DeleteTarget(TEST_USER, destDir, null, false));
  }

  @Test
  @WithMockUser(username = TEST_USER)
  public void testDeleteDir_force_not_found() throws IOException {

    String url = ApiPath.DELETE_DIR_API + "/{path}";
    Function<UriBuilder, URI> uriBuilder = (builder -> builder.path(url).queryParam("force", "true")
        .build("invalid"));

    client.post().uri(uriBuilder)
        .exchange()
        .expectStatus().isNotFound();
  }

}
