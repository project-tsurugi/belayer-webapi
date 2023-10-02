package com.tsurugidb.belayer.webapi.service;

import static org.junit.jupiter.api.Assertions.fail;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import com.tsurugidb.belayer.webapi.exception.BadRequestException;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK, properties = "webapi.storage.root=./test_tmp")
public class FileSystemServiceTest {

    @Value("${webapi.storage.root}")
    private String storageRootDir;

    @Autowired
    FileSystemService fileSystemService;

    private static final String TEST_USER = "test_user";

    @BeforeEach
    public void setUp() throws IOException {
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
    public void test_createDirectory_invalid() throws Exception {
        try {
            fileSystemService.createDirectory(TEST_USER, "../foo");
            fail("not thrown");
        } catch (BadRequestException ex) {
            // OK
        }
    }

    @Test
    public void test_checkDirPath() throws Exception {
        fileSystemService.createDirectory(TEST_USER, "foo");
        fileSystemService.checkDirPath(TEST_USER, "foo");
    }

    @Test
    public void test_checkDirPath_invalid() throws Exception {
        fileSystemService.createDirectory(TEST_USER, "foo");
        try {
            fileSystemService.checkDirPath(TEST_USER, "../bar");
            fail("not thrown");
        } catch (BadRequestException ex) {
            // ok
        }
    }

    @Test
    public void test_checkDirExists() throws Exception {

        fileSystemService.createDirectory(TEST_USER, "foo");
        fileSystemService.checkDirExists(TEST_USER, "foo");
    }


    @Test
    public void test_checkDirExists_invalid() throws Exception {
        try {
            fileSystemService.checkDirExists(TEST_USER, "bar");
            fail("not thrown");
        } catch (BadRequestException ex) {
            // ok
        }
    }

    @Test
    public void test_checkFileExists() throws Exception {
        String destDir = "dir_for_test";
        String fileName = "test-file-to-download.txt";
        String filePath = destDir + "/" + fileName;
        String contents = "this is test file";

        Path dir = Path.of(storageRootDir, TEST_USER, destDir);
        Files.createDirectories(dir);
        Files.write(Path.of(dir.toString(), fileName), contents.getBytes(StandardCharsets.US_ASCII));

        fileSystemService.checkFileExists(TEST_USER, filePath);
    }

    @Test
    public void test_checkFileExists_invalid() throws Exception {
        try {
            fileSystemService.checkFileExists(TEST_USER, "bar.txt");
            fail("not thrown");
        } catch (BadRequestException ex) {
            // ok
        }
    }

    @Test
    public void test_delteFile() throws Exception {
        String destDir = "dir_for_test";
        String fileName = "test-file-to-download.txt";
        String filePath = destDir + "/" + fileName;
        String contents = "this is test file";

        Path dir = Path.of(storageRootDir, TEST_USER, destDir);
        Files.createDirectories(dir);
        Files.write(Path.of(dir.toString(), fileName), contents.getBytes(StandardCharsets.US_ASCII));

        fileSystemService.deleteFile(TEST_USER, filePath);
    }

}
