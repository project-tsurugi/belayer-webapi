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

    try (var out = new FileOutputStream(distPath.toString())) {
      Files.copy(realFilePath, out);
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

    // prevent Dir Traversal
    Path dirPath = resolveInUserDir(uid, dir, "Invalid destination dir:");
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

  /**
   * Create a new temporary directory in the default temporary-file directory.
   *
   * @param prefix prefix of the directory name
   * @return path of created directory.
   * @throws IORuntimeException I/O Error
   */
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

    // prevent directory traversal
    Path destDirPath = resolveInUserDir(uid, toDir);
    log.debug("check dir path. dest:{}", destDirPath);
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
   * @throws BadRequestException if file is not exists, or the path points outside the user's storage area.
   */
  public void checkFileExists(String uid, String filePath) throws BadRequestException {
    if (StringUtil.isEmpty(filePath)) {
      throw new BadRequestException("Invalid path. path:" + filePath,
          "Invalid path. path:" + filePath, null);
    }

    // prevent directory traversal
    Path fp = resolveInUserDir(uid, filePath);

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
   * @throws BadRequestException if the path points outside the user's storage area.
   */
  public Path convertToAbsolutePath(String uid, String downloadPath) {
    return resolveInUserDir(uid, downloadPath);
  }

  /**
   * Return the absolute, normalized path of the user's storage area.
   *
   * @param uid User ID
   * @return the user's storage directory
   */
  public Path getUserDir(String uid) {
    return Path.of(storageRootDir, uid).toAbsolutePath().normalize();
  }

  /**
   * Resolve a path given by a client against the user's storage area.
   *
   * <p>
   * The path is normalized, and it must stay inside the user's storage area.
   * An absolute path is treated as relative to the storage area.
   * Symbolic links are not resolved.
   * </p>
   *
   * @param uid  User ID
   * @param path path relative to the user's storage area
   * @return the absolute, normalized path
   * @throws BadRequestException if the path points outside the user's storage area.
   */
  public Path resolveInUserDir(String uid, String path) throws BadRequestException {
    return resolveInUserDir(uid, path, "Invalid path. path:");
  }

  /**
   * Resolve a path given by a client against the user's storage area.
   *
   * <p>
   * Same as {@link #resolveInUserDir(String, String)}, with the message of the error.
   * </p>
   *
   * @param uid     User ID
   * @param path    path relative to the user's storage area
   * @param message prefix of the error message. The path is appended to it.
   * @return the absolute, normalized path
   * @throws BadRequestException if the path points outside the user's storage area.
   */
  public Path resolveInUserDir(String uid, String path, String message) throws BadRequestException {
    Path userDir = getUserDir(uid);
    Path resolved = Path.of(storageRootDir, uid, path).toAbsolutePath().normalize();
    if (!resolved.startsWith(userDir)) {
      throw new BadRequestException(message + path, message + resolved, null);
    }
    return resolved;
  }

  /**
   * Convert download path from absolute path.
   * 
   * @param uid          User ID
   * @param absolutePath absolute path
   * @return download Path
   */
  public Path convertToDownloadPath(String uid, String absolutePath) {
    Path downloadRootPath = getUserDir(uid);
    Path path = Path.of(absolutePath).toAbsolutePath().normalize();

    return downloadRootPath.relativize(path);
  }

  /**
   * Return list of paths under the target directory (up to 10 levels deep), sorted in natural order.
   *
   * The target directory itself is included unless directories are hidden.
   *
   * @param targetDir directory to list
   * @param hideFile  if true, list directories only
   * @param hideDir   if true (and hideFile is false), list files only
   * @return list of normalized paths
   * @throws IOException I/O Error while walking the directory
   */
  public List<String> getFileList(Path targetDir, boolean hideFile, boolean hideDir) throws IOException {

    try (var paths = Files.walk(targetDir, MAX_DEPTH)) {
      return paths
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
    }
  }

  /**
   * Delete file in user storage.
   *
   * @param uid      User ID
   * @param filePath download path of the file to delete
   * @throws IORuntimeException I/O Error
   * @throws BadRequestException if the path points outside the user's storage area.
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
    try (var paths = Files.walk(path)) {
      paths
          .sorted(Comparator.reverseOrder())
          .map(Path::toFile)
          .forEach(File::delete);
    } catch (IOException ex) {
      throw new IORuntimeException("Can't delete directry.", ex);
    }
  }

}
