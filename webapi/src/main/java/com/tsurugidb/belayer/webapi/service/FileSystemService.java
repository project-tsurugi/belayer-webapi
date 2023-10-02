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
package com.tsurugidb.belayer.webapi.service;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jetty.util.StringUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.tsurugidb.belayer.webapi.exception.BadRequestException;
import com.tsurugidb.belayer.webapi.exception.IORuntimeException;
import com.tsurugidb.belayer.webapi.exception.InternalServerErrorException;

import lombok.extern.slf4j.Slf4j;

/**
 * File System Service
 */
@Slf4j
@Component
public class FileSystemService {

  @Value("${webapi.storage.root}")
  private String storageRootDir;

  private static final int MAX_DEPTH = 10;

  /**
   * Copy file.
   * 
   * @param realFilePath target file path
   * @param toDir        destination directory path
   * @return real path of copied file
   * @throws IORuntimeException I/O Error
   */
  public String copyTo(Path realFilePath, Path toDir) throws IORuntimeException {

    String fileName = realFilePath.getFileName().toString();

    log.debug("fileName:{}", fileName);
    Path distPath = Path.of(toDir.toString(), fileName);

    log.debug("start copy:" + realFilePath + "->" + distPath);

    try {
      Files.copy(realFilePath, new FileOutputStream(distPath.toString()));
      log.debug("end copy:" + realFilePath + "->" + distPath);
    } catch (IOException ex) {
      throw new IORuntimeException("I/O error.", ex);
    }

    log.debug("dist path:" + distPath);

    return distPath.toString();
  }

  /**
   * Create specified directory.
   * 
   * @param uid userId
   * @param dir Directory path to create
   * @return path of created directory.
   */
  public Path createDirectory(String uid, String dir) {
    log.debug("create dir: " + uid + "/" + dir);

    Path dirPath = Path.of(storageRootDir, uid, dir).toAbsolutePath().normalize();
    int storageDirNameCount = Path.of(storageRootDir, uid).getNameCount();
    int dirFullPathNameCount = dirPath.getNameCount();
    int dirNameCount = storageDirNameCount < dirFullPathNameCount
        ? dirPath.subpath(storageDirNameCount, dirFullPathNameCount).getNameCount()
        : 0;

    log.debug("dir count: " + dirNameCount);

    // prevent Dir Traversal
    Path downloadRoot = Path.of(storageRootDir, uid).toAbsolutePath().normalize();
    if (!dirPath.startsWith(downloadRoot.toString()) || dirNameCount < 1) {
      throw new BadRequestException("Invalid destination dir:" + dir,
          "invalid destination dir. dir:" + dirPath, null);
    }
    log.debug("save ok");

    // create dir
    try {
      log.debug("create dir: " + dirPath);
      Files.createDirectories(dirPath);
      return dirPath;

    } catch (IOException ex) {
      throw new InternalServerErrorException("Can't create dir:" + dirPath, ex);
    }
  }

  public Path createTempDirectory(String prefix) {
    try {
      return Files.createTempDirectory(prefix);
    } catch (IOException ex) {
      throw new IORuntimeException("failed to create a temp directory.", ex);
    }
  }

  /**
   * Check directory path is valid.
   * 
   * @param uid   User ID
   * @param toDir destination directory path
   */
  public void checkDirPath(String uid, String toDir) {
    if (StringUtil.isEmpty(toDir)) {
      throw new BadRequestException("Invalid path. path:" + toDir,
          "Invalid path. path:" + toDir, null);
    }

    Path downlaodRootPath = Path.of(storageRootDir, uid).toAbsolutePath().normalize();
    Path destDirPath = Path.of(storageRootDir, uid, toDir).toAbsolutePath().normalize();

    log.debug("check dir path. root:{}, dest:{}", downlaodRootPath, destDirPath);
    // prevent directory traversal
    if (!destDirPath.startsWith(downlaodRootPath)) {
      throw new BadRequestException("Invalid path. path:" + toDir,
          "Invalid path. path:" + destDirPath, null);
    }

  }

  /**
   * Check directory exists.
   * 
   * @param uid   User ID
   * @param toDir directory path
   * @throws BadRequestException if directory is not exists.
   */
  public void checkDirExists(String uid, String toDir) throws BadRequestException {
    if (StringUtil.isEmpty(toDir)) {
      throw new BadRequestException("Invalid path. path:" + toDir,
          "Invalid path. path:" + toDir, null);
    }

    Path destDirPath = Path.of(storageRootDir, uid, toDir).toAbsolutePath().normalize();

    log.debug("check dir path. dest:{}", destDirPath);
    if (!Files.isDirectory(destDirPath)) {
      throw new BadRequestException("Invalid path. path:" + toDir,
          "Invalid path. path:" + destDirPath, null);
    }
  }

  /**
   * Check File exists.
   * 
   * @param uid      User ID
   * @param filePath directory path
   * @throws BadRequestException if file is not exists.
   */
  public void checkFileExists(String uid, String filePath) throws BadRequestException {
    if (StringUtil.isEmpty(filePath)) {
      throw new BadRequestException("Invalid path. path:" + filePath,
          "Invalid path. path:" + filePath, null);
    }

    Path fp = Path.of(storageRootDir, uid, filePath).toAbsolutePath().normalize();

    log.debug("check file path. dest:{}", fp);
    if (!new File(fp.toString()).isFile()) {
      String message = "Invalid path. path:" + filePath;
      throw new BadRequestException(message, message, null);
    }
  }

  /**
   * Convert absolute path form download path.
   * 
   * @param uid          User ID
   * @param downloadPath download path
   * @return Absolute Path
   */
  public Path convertToAbsolutePath(String uid, String downloadPath) {
    return Path.of(storageRootDir, uid, downloadPath).toAbsolutePath().normalize();
  }

  /**
   * Convert download path from absolute path.
   * 
   * @param uid          User ID
   * @param absolutePath absolute path
   * @return download Path
   */
  public Path convertToDownloadPath(String uid, String absolutePath) {
    Path downloadRootPath = Path.of(storageRootDir, uid).toAbsolutePath().normalize();
    Path path = Path.of(absolutePath).toAbsolutePath().normalize();

    return downloadRootPath.relativize(path);
  }

  public List<String> getFileList(Path targetDir, boolean hideFile, boolean hideDir) throws IOException {

    List<String> fileList = Files.walk(targetDir, MAX_DEPTH)
        .filter(path -> {
          if (hideFile) {
            // show dir only
            return Files.isDirectory(path);
          } else if (hideDir) {
            // show file only
            return !Files.isDirectory(path) && Files.exists(path);
          } else {
            // show both file and dir
            return true;
          }
        })
        .map(path -> {
          return path.normalize().toString();
        })
        .sorted(Comparator.naturalOrder())
        .collect(Collectors.toList());
    return fileList;

  }

  /**
   * Delete file in user storage.
   */
  public void deleteFile(String uid, String filePath) {
    try {
      Path fullPath = convertToAbsolutePath(uid, filePath);
      log.debug("delete file:" + fullPath);
      Files.delete(fullPath);
    } catch (IOException ex) {
      throw new IORuntimeException("Can not delete uploaded file.", ex);
    }
  }

  /**
   * Delete directry with all contents.
   * 
   * @param path ditrectory path to delete.
   */
  public void deleteDirectoryWithContent(Path path) {
    try {
      Files.walk(path)
          .sorted(Comparator.reverseOrder())
          .map(Path::toFile)
          .forEach(File::delete);
    } catch (IOException ex) {
      throw new IORuntimeException("Can't delete directry.", ex);
    }
  }

}
