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
package com.tsurugidb.belayer.webapi.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ZipFileUtilTest {

    String saveDir = "./test_tmp/aaa/";

    @BeforeEach
    public void setUp() throws IOException {
        Files.createDirectories(Path.of(saveDir, "foo"));
    }

    @AfterEach
    public void tearDown() throws IOException {
        // delete dir for test
        Files.walk(Path.of("./test_tmp"))
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
    }

    @Test
    public void testCollectAsZip() throws Exception {

        List<String> list = List.of(saveDir + "foo/foo1.txt",
                saveDir + "foo/foo2.txt");

        byte[] contents = "test file".getBytes();

        for (String path : list) {
            Files.write(Path.of(path), contents);
        }

        String distPath = saveDir + "test.zip";

        list.stream().collect(ZipFileUtil.collectAsZipFile(Path.of(saveDir), distPath, -1));

        assertEquals(true, Files.exists(Path.of(distPath)) && !Files.isDirectory(Path.of(distPath)));
    }
}
