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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import com.tsurugidb.belayer.webapi.exception.IORuntimeException;

import lombok.extern.slf4j.Slf4j;

/**
 * Utilities for zip files.
 */
@Slf4j
public class ZipFileUtil {

    /**
     * Return a collector that writes the files of the given paths into a zip file.
     *
     * Each entry is named by the path relative to the base path. The collector does not support parallel streams.
     *
     * @param basePath      base path to name the zip entries.
     * @param zipFileName   path of the zip file to create.
     * @param compressLevel compression level of the zip file.
     * @return collector that returns the path of the created zip file.
     */
    public static Collector<String, ?, String> collectAsZipFile(Path basePath, String zipFileName, int compressLevel) {

        log.debug("basePath:{}, zipFileName:{}", basePath, zipFileName);

        Supplier<ZipOutputStream> supplier = () -> {
            try {
                Path parentDir = Path.of(zipFileName).getParent();
                log.debug("parentDir:{}", parentDir);
              
                Files.createDirectories(parentDir);
                log.debug("dir exists:{}" , Files.isDirectory(parentDir));
                FileOutputStream fos = new FileOutputStream(zipFileName);
                ZipOutputStream zos = new ZipOutputStream(fos);
                zos.setLevel(compressLevel);
                return zos;
            } catch (FileNotFoundException ex) {
                throw new IORuntimeException("Zip file path is invalid.", ex);
            } catch (IOException ex) {
                throw new IORuntimeException("Can not create a directory to save a zip file.", ex);
            }
        };

        BiConsumer<ZipOutputStream, String> accumulator = (zos, filePath) -> {
            synchronized (zos) {
                try {
                    log.debug("zip file path:{}", filePath);
                    ZipEntry entry = new ZipEntry(toFilePath(basePath, filePath));
                    zos.putNextEntry(entry);
                    zos.write(getFileContents(filePath));
                    zos.closeEntry();
                } catch (IOException ex) {
                    throw new IORuntimeException("I/O Error occurred while writing a zip file.", ex);
                }
            }
        };

        BinaryOperator<ZipOutputStream> combiner = (list1, list2) -> {
            throw new UnsupportedOperationException("Combining multiple streams is not supported.");
        };

        Function<ZipOutputStream, String> finisher = z -> {
            try {
                z.close();
                return zipFileName;
            } catch (IOException ex) {
                throw new IORuntimeException("I/O Error occurred while writing a zip file.", ex);
            }

        };

        return Collector.of(supplier, accumulator, combiner, finisher);
    }

    private static String toFilePath(Path basePath, String filePath) {
        log.debug("base:{}, path:{}", basePath, filePath);
        return basePath.relativize(Path.of(filePath)).toString();
    }

    private static byte[] getFileContents(String filePath) {
        try {
            return Files.readAllBytes(Path.of(filePath));
        } catch (IOException ex) {
            throw new IORuntimeException("I/O Error occurred while reading a file.", ex);
        }
    }

    /**
     * Extract a zip file into the destination directory.
     *
     * @param destDir     destination directory.
     * @param zipFilePath path of the zip file.
     * @throws IORuntimeException if an I/O error occurred, or an entry is outside of the destination directory.
     */
    public static void extractZipFile(Path destDir, Path zipFilePath) {
        Objects.requireNonNull(destDir, "destDir");
        Objects.requireNonNull(zipFilePath, "zipFilePath");

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFilePath.toFile()))) {

            ZipEntry zipEntry = zis.getNextEntry();

            byte[] buffer = new byte[1024];
            while (zipEntry != null) {
                File newFile = newFile(destDir.toFile(), zipEntry);
                if (zipEntry.isDirectory()) {
                    if (!newFile.isDirectory() && !newFile.mkdirs()) {
                        throw new IOException("Failed to create directory " + newFile);
                    }
                } else {
                    // fix for Windows-created archives
                    File parent = newFile.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) {
                        throw new IOException("Failed to create directory " + parent);
                    }

                    // write file content
                    log.debug("extract file:" + newFile);
                    FileOutputStream fos = new FileOutputStream(newFile);
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                    fos.close();
                }
                zis.closeEntry();
                zipEntry = zis.getNextEntry();
            }

        } catch (IOException ex) {
            throw new IORuntimeException("I/O Error occurred while extract a zip file.", ex);
        }
    }

    private static File newFile(File destinationDir, ZipEntry zipEntry) throws IOException {
        File destFile = new File(destinationDir, zipEntry.getName());

        String destDirPath = destinationDir.getCanonicalPath();
        String destFilePath = destFile.getCanonicalPath();

        if (!destFilePath.startsWith(destDirPath + File.separator)) {
            throw new IOException("Entry is outside of the target dir: " + zipEntry.getName());
        }

        return destFile;
    }
}
