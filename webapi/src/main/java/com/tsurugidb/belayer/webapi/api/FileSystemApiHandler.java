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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.FormFieldPart;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.tsurugidb.belayer.webapi.api.helper.UploadHelper;
import com.tsurugidb.belayer.webapi.api.helper.UploadHelper.UploadParameter;
import com.tsurugidb.belayer.webapi.dto.DeleteTarget;
import com.tsurugidb.belayer.webapi.dto.DownloadPathList;
import com.tsurugidb.belayer.webapi.dto.DownloadZip;
import com.tsurugidb.belayer.webapi.exception.BadRequestException;
import com.tsurugidb.belayer.webapi.exception.InternalServerErrorException;
import com.tsurugidb.belayer.webapi.exception.NotFoundException;
import com.tsurugidb.belayer.webapi.model.Constants;
import com.tsurugidb.belayer.webapi.model.ZipFileUtil;
import com.tsurugidb.belayer.webapi.model.SystemTime;
import com.tsurugidb.belayer.webapi.service.FileSystemService;
import com.tsurugidb.belayer.webapi.service.ParquetService;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Handler for FileSystem APIs.
 */
@Slf4j
@Component
public class FileSystemApiHandler {

  @Value("${webapi.storage.root}")
  private String storageRootDir;

  @Value("${webapi.download.zipcompresslevel}")
  private int zipCompressLevel;

  @Value("${webapi.list.max.files}")
  private int listFileMaxSize;

  @Autowired
  private UploadHelper uploadHelper;

  @Autowired
  private FileSystemService fileSystemService;

  @Autowired
  private ParquetService parquetService;

  @Autowired
  private SystemTime systemTime;

  @PostConstruct
  public void validateProperties() {
    if (zipCompressLevel < -1 || zipCompressLevel > 9) {
      throw new IllegalArgumentException("zipCompressLevel must be between 0 and 9 or -1.");
    }
  }

  /**
   * Upload API Handler
   *
   * @param request Request
   * @return Response
   * @throws IOException
   */
  public Mono<ServerResponse> uploadFiles(final ServerRequest request) {

    uploadHelper.checkRequestHeader(request);

    var param = new UploadParameter();

    return ReactiveSecurityContextHolder.getContext()
        .map(SecurityContext::getAuthentication)
        .map(auth -> this.fillParams(auth, request, param))
        .flatMap(p -> this.extractParamFromMultipart(request, param))
        .flatMapMany(p -> this.saveFile(param))
        .collectList()
        .flatMap(res -> ServerResponse.ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromProducer(Mono.just(new DownloadPathList(res, null)), DownloadPathList.class)));
  }

  private UploadParameter fillParams(Authentication auth, ServerRequest req, UploadParameter param) {

    param.setUid(auth.getName());
    param.setCredentials(auth.getCredentials());

    return param;
  }

  private Mono<UploadParameter> extractParamFromMultipart(ServerRequest request, UploadParameter param) {

    return request.body(BodyExtractors.toParts())
        .map(part -> {
          if (part instanceof FilePart && part.name().equals("file")) {
            var filePart = (FilePart) part;
            param.addFilePart(filePart);

          } else if (part instanceof FormFieldPart) {
            var name = part.name();
            var value = ((FormFieldPart) part).value();
            if ("overwrite".equals(name)) {
              param.setOverwrite(Boolean.valueOf(value));
            } else if ("destDir".equals(name)) {
              param.setDestDirPath(value);
            }
          }
          return "dumy";
        })
        .collectList()
        .map(dummy -> {
          // when 0 files uploaded
          var list = param.getFileParts();
          if (list.size() == 0) {
            throw new BadRequestException("No files to upload.",
                "no files to upload.", null);
          }

          var destDir = fileSystemService.createDirectory(param.getUid(), param.getDestDirPath());
          return destDir;
        })
        .then(Mono.just(param));
  }

  private Flux<String> saveFile(UploadParameter param) {
    var destDir = fileSystemService.convertToAbsolutePath(param.getUid(), param.getDestDirPath());
    return Flux.fromIterable(param.getFileParts())
        .flatMap(filePart -> {
          var realFilePath = Path.of(destDir.toString(), filePart.filename());
          Resource resource = new FileSystemResource(realFilePath);
          if (Files.exists(realFilePath) && resource.isFile()) {
            if (param.isOverwrite()) {
              log.debug("overwrite file: " + realFilePath);
            } else {
              var msg = String.format("target file exists. file:{}, dir:{}", realFilePath.toString(),
                  destDir.toString());
              throw new BadRequestException(msg, msg);
            }
          }

          return uploadHelper.saveFile(param.getUid(), destDir, filePart);
        })
        .map(realPath -> {
          var downloadPath = fileSystemService.convertToDownloadPath(param.getUid(), realPath);
          return downloadPath.toString();
        });

  }

  /**
   * Download API Handler
   *
   * curl -v localhost:8000/api/download/<path_to_file> -H "Authorization: Beaer
   * $ACCESS_TOKEN"
   * NOTE: "/" in path should be replaced by "%2F".
   *
   * @param req Request
   * @return Response
   */
  public Mono<ServerResponse> downloadFile(ServerRequest req) {

    String filePath = req.pathVariable("filepath");
    boolean convertToCsv = Boolean.valueOf(req.queryParam("csv").orElse("false"));

    return ReactiveSecurityContextHolder.getContext()
        .map(SecurityContext::getAuthentication)
        .map(Authentication::getName)
        .map(uid -> getFileResource(uid, filePath, convertToCsv))
        .flatMap(resource -> createBinaryRespose(resource, resource.getFilename()));
  }

  private Resource getFileResource(String uid, String filePath, boolean convertToCsv) {

    Path path = Path.of(storageRootDir, uid, filePath).toAbsolutePath().normalize();

    log.info("download: " + path);

    // prevent Directory Traversal
    Path userDir = Path.of(storageRootDir, uid).toAbsolutePath().normalize();
    log.info("userDir: " + userDir);
    if (!path.startsWith(userDir)) {
      throw new BadRequestException("Invalid file path. path:" + filePath,
          "Invalid file path. path:" + path, null);
    }

    // check file is exists.
    if (!Files.exists(path)) {
      throw new BadRequestException("File or Directory not found. path:" + filePath,
          "File or Directory not found. path:" + path, null);
    }

    // create zip when directory is specified
    if (Files.isDirectory(path)) {
      return createZipResource(uid, path);
    }

    Resource resource = new FileSystemResource(path);

    // check file is readable.
    if (!resource.isReadable()) {
      throw new InternalServerErrorException("File is not readable. path:" + path, null);
    }

    // convert parquet to csv if necessary
    if (!convertToCsv) {
      return resource;
    }

    return convertToParquetToCsv(path);
  }

  private Resource createZipResource(String uid, Path dirPath) {

    String fileName = dirPath.getName(dirPath.getNameCount() - 1).toString() + ".zip";
    var tempDir = fileSystemService.createTempDirectory(Constants.TEMP_DIR_PREFIX_DOWNLOAD);
    String zipFilePath = tempDir.toString() + "/" + fileName;
    String downloadPath = fileSystemService.convertToDownloadPath(uid, dirPath.toString()).toString();
    log.debug("dirPath:{},zipFilePath:{},downloadPath:{}", dirPath, zipFilePath, downloadPath);
    String zipFileName = getFileList(uid, downloadPath, false, true).stream()
        .map(it -> {
          log.debug("filePath:" + it);
          return dirPath + it.replaceFirst(downloadPath, "").toString();
        })
        .collect(ZipFileUtil.collectAsZipFile(dirPath, zipFilePath, zipCompressLevel));
    return new FileSystemResource(zipFileName);
  }

  private Resource convertToParquetToCsv(Path parquetPath) {

    String parquetFilePath = parquetPath.toString();

    if (!parquetFilePath.endsWith(Constants.EXT_PARQUET)) {
      String msg = "Target is not a parquet file.";
      String reason = msg + " path:" + parquetFilePath;
      throw new BadRequestException(msg, reason);
    }

    String csvFilePath = parquetFilePath.replaceAll(Constants.EXT_PARQUET + '$', Constants.EXT_CSV);
    log.debug("paquet:{}, csv:{}", parquetFilePath, csvFilePath);
    parquetService.convertParquetToCsv(parquetFilePath, csvFilePath);

    return new FileSystemResource(csvFilePath);
  }

  private Mono<ServerResponse> createBinaryRespose(Resource resource, String filePath) {

    return ServerResponse
        .ok().cacheControl(CacheControl.noCache())
        .header("Content-Type", "application/octet-stream")
        .header("Content-Disposition", "attachment; filename=" + filePath)
        .body(BodyInserters.fromResource(resource));

  }

  /**
   * DownloadZip API Handler
   *
   * curl -X POST -H "Content-Type: application/json" -d '{"pathList":
   * ["dir1/FOO_TBL0.parquet", "dir1/FOO_TBL1.parquet"}' localhost:8000/api/downloadzip
   * -H "Authorization: Beaer $ACCESS_TOKEN"* *
   *
   * @param req Request
   * @return Response

   */
  public Mono<ServerResponse> downloadzipFile(ServerRequest req) {

    boolean convertToCsv = Boolean.valueOf(req.queryParam("csv").orElse("false"));

    return ReactiveSecurityContextHolder.getContext()
      .map(SecurityContext::getAuthentication)
      .flatMap(auth -> downloadParams(auth, req))
      .map(param -> getMultipleFileResource(param.getUid(), param.getPathList(), convertToCsv))
      .flatMap(resource -> createBinaryRespose(resource, resource.getFilename()));
  }

  private Resource getMultipleFileResource(String uid, String[] filePathList, boolean convertToCsv) {

    if (filePathList.length < 1) {
      throw new BadRequestException("One or more filePaths must be specified.", null);
    }

    // get parent dir
    String parentDir = Arrays.stream(filePathList)
        .map(Paths::get)
        .map(Path::getParent)
        .map(Path::toString)
        .distinct()
        .reduce((dir1, dir2) -> dir1.equals(dir2) ? dir1 : "")
        .orElse("");

    if (parentDir == null || parentDir.toString().isEmpty()) {
      throw new BadRequestException("Only files with the same parent directory can be included.", null);
    }

    Path dirPath = Path.of(storageRootDir, uid, parentDir).toAbsolutePath().normalize();
    String fileName = Constants.FILE_PREFIX_MULTIPLE_DOWNLOAD_ZIP + createTimeStamp() + Constants.EXT_ZIP;

    Path tempDir = fileSystemService.createTempDirectory(Constants.TEMP_DIR_PREFIX_DOWNLOAD);
    String zipFilePath = tempDir.toString() + "/" + fileName;

    String zipFileName = Arrays.stream(filePathList)
    .map(filepath -> getFileResource(uid, filepath, filepath.endsWith(Constants.EXT_PARQUET) ? convertToCsv : false))
    .map(file -> getFilePath(file))
    .collect(ZipFileUtil.collectAsZipFile(dirPath, zipFilePath, zipCompressLevel));

    return new FileSystemResource(zipFileName);
  }

  private String createTimeStamp() {
      var now = systemTime.now();
      return  DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
        .format(LocalDateTime.ofInstant(now, ZoneOffset.UTC));
  }

  private String getFilePath(Resource resource) {
    try {
      return resource.getURI().getPath();
    } catch (Exception ex) {
      throw new BadRequestException("Failed to get File or Directory resource", null);
    }
  }

  private Mono<DownloadZip> downloadParams(Authentication auth, ServerRequest req) {

    return req.bodyToMono(DownloadZip.class)
        .switchIfEmpty(Mono.just(new DownloadZip()))
        // fill params
        .map(param -> {
          param.setUid(auth.getName());
          return param;
        });
  }

  /**
   * List Files API Handler
   *
   * curl -v localhost:8000/api/dirlist/<path_to_file> -H "Authorization: Beaer
   * $ACCESS_TOKEN"
   * NOTE: "/" in path should be replaced by "%2F".
   *
   * @param req Request
   * @return Response
   */
  public Mono<ServerResponse> listFiles(ServerRequest req) {
    String dirPath = req.pathVariable("dirpath");
    Optional<String> hide_file = req.queryParam("hide_file");
    Optional<String> hide_dir = req.queryParam("hide_dir");

    final boolean hideFile = Boolean.valueOf(hide_file.orElse("false"));
    final boolean hideDir = Boolean.valueOf(hide_dir.orElse("false"));
    if (hideFile && hideDir) {
      throw new BadRequestException(
          "You can not specify both \"hide_file\" and \"hide_dir\". Specify one of these or do not specify either.",
          "", null);
    }

    return ReactiveSecurityContextHolder.getContext()
        .map(SecurityContext::getAuthentication)
        .map(Authentication::getName)
        .map(uid -> getFileList(uid, dirPath, hideFile, hideDir))
        .flatMap(list -> {
          List<String> result = list;
          String message = null;
          if (list.size() > listFileMaxSize) {
            result = list.subList(0, listFileMaxSize);
            message = "List size is over limit. size=" + list.size();
          }
          return ServerResponse.ok()
              .contentType(MediaType.APPLICATION_JSON)
              .body(
                  BodyInserters.fromProducer(Mono.just(new DownloadPathList(result, message)), DownloadPathList.class));
        });
  }

  private List<String> getFileList(String uid, String dirPath, boolean hideFile, boolean hideDir) {

    Path downloadRootPath = Path.of(storageRootDir, uid).toAbsolutePath().normalize();

    try {
      // avoid lack of user dir.
      Files.createDirectories(downloadRootPath);

      Path targetDir = Path.of(storageRootDir, uid, dirPath).toAbsolutePath().normalize();

      log.debug("root:{}, target:{}", downloadRootPath, targetDir);

      // prevent directory traversal
      if (!targetDir.startsWith(downloadRootPath)) {
        throw new BadRequestException("Invalid path. path:" + dirPath,
            "invalid path. path:" + targetDir, null);
      }

      // check dir exists
      if (!Files.isDirectory(targetDir)) {
        log.debug("targetDir:{}", targetDir);
        throw new NotFoundException("Directory Not Found", "dir not found. dir:" + targetDir, null);
      }

      return fileSystemService.getFileList(targetDir, hideFile, hideDir)
          .stream()
          .map(path -> {
            String relativePath = downloadRootPath.relativize(Path.of(path)).toString();
            if (Files.isDirectory(Path.of(path))) {
              return relativePath + "/";
            }
            return relativePath;
          })
          .collect(Collectors.toList());

    } catch (IOException ex) {
      // log.info("error occured.", ex);
      throw new InternalServerErrorException("Unreadable directory info. path:" + dirPath, ex);
    }

  }

  /**
   * Delete File API Handler
   *
   * curl -v localhost:8000/api/delete/file/<path_to_file> -H "Authorization:
   * Beaer
   * $ACCESS_TOKEN"
   * NOTE: "/" in path should be replaced by "%2F".
   *
   * @param req Request
   * @return Response
   */
  public Mono<ServerResponse> deleteFile(ServerRequest req) {

    return ReactiveSecurityContextHolder.getContext()
        .map(SecurityContext::getAuthentication)
        .flatMap(auth -> fillDeleteParams(auth, req, false))
        .map(param -> {
          param.setPathList(new String[] { param.getPath() });
          return param;
        })
        .flatMap(param -> deleteFileOrDir(param.getUid(), param.getPathList(), true, false));
  }

  /**
   * Delete Files API Handler
   *
   * curl -X POST -H "Content-Type: application/json" -d '{"pathList":
   * ["file1.txt", "file2.txt"}' localhost:8000/api/delete/files
   * -H "Authorization: Beaer $ACCESS_TOKEN"
   *
   * @param req Request
   * @return Response
   */
  public Mono<ServerResponse> deleteFiles(ServerRequest req) {

    return ReactiveSecurityContextHolder.getContext()
        .map(SecurityContext::getAuthentication)
        .flatMap(auth -> fillDeleteParams(auth, req, true))
        .flatMap(target -> deleteFileOrDir(target.getUid(), target.getPathList(), true, false));
  }

  private Mono<DeleteTarget> fillDeleteParams(Authentication auth, ServerRequest req, boolean deleteMultiFile) {

    return req.bodyToMono(DeleteTarget.class)
        .switchIfEmpty(Mono.just(new DeleteTarget()))
        // fill params
        .map(param -> {
          param.setUid(auth.getName());
          return param;
        });
  }

  /**
   * Delete Directory API Handler
   *
   * curl -v localhost:8000/api/delete/dir/<path_to_dir> -H "Authorization:
   * Beaer
   * $ACCESS_TOKEN"
   * NOTE: "/" in path should be replaced by "%2F".
   *
   * @param req Request
   * @return Response
   */
  public Mono<ServerResponse> deleteDir(ServerRequest req) {

    return ReactiveSecurityContextHolder.getContext()
        .map(SecurityContext::getAuthentication)
        .flatMap(auth -> fillDeleteParams(auth, req, false))
        .flatMap(param -> deleteFileOrDir(param.getUid(), new String[] { param.getPath() }, false, param.isForce()));
  }

  private Mono<ServerResponse> deleteFileOrDir(String uid, String[] filePathList, boolean targetIsFile, boolean force) {
    Path userDir = Path.of(storageRootDir, uid).toAbsolutePath().normalize();

    if (!targetIsFile) {
      // delete directory

      if (filePathList == null || filePathList.length == 0) {
        // not reach here.
        throw new IllegalArgumentException("dir param is empty.");
      }

      Path absPath = Path.of(storageRootDir, uid, filePathList[0]).toAbsolutePath().normalize();
      log.debug("delete dir:" + absPath);
      if (!absPath.startsWith(userDir)) {
        throw new BadRequestException("Invalid directory path. path:" + filePathList[0],
            "Invalid directory path. path:" + absPath, null);
      }

      try {
        var absFile = absPath.toFile();
        if (!absFile.isDirectory()) {
          throw new FileNotFoundException("Specified path is a file. path:" + absPath);
        }

        if (force) {
          FileUtils.forceDelete(absPath.toFile());
        } else {
          Files.delete(absPath);
        }
      } catch (NoSuchFileException | FileNotFoundException ex) {
        throw new NotFoundException("Directory not Found. path:" + filePathList[0],
            "Direcotry not found. dir:" + absPath.toString(), null);
      } catch (DirectoryNotEmptyException ex) {
        throw new BadRequestException("Directory is not empty. path:" + filePathList[0],
            "Direcotry is not empty. dir:" + absPath.toString(), null);
      } catch (IOException ex) {
        throw new InternalServerErrorException("Can't delete directory. path:" + filePathList[0], ex);
      }

      return ServerResponse.ok()
          .contentType(MediaType.APPLICATION_JSON)
          .body(
              BodyInserters.fromProducer(Mono.just(new DeleteTarget(uid, filePathList[0], null, false)),
                  DeleteTarget.class));
    }

    // delete single file
    for (String filePath : filePathList) {
      Path absPath = Path.of(storageRootDir, uid, filePath).toAbsolutePath().normalize();
      log.debug("delete file:" + absPath);
      if (!absPath.startsWith(userDir)) {
        throw new BadRequestException("Invalid file path. path:" + filePath,
            "Invalid file path. path:" + absPath, null);
      }

      try {
        var absFile = absPath.toFile();
        if (absFile.isDirectory()) {
          throw new NoSuchFileException("Specified path is a directory. path:" + absPath);
        }

        Files.delete(absPath);
      } catch (NoSuchFileException ex) {
        throw new NotFoundException("File not Found. path:" + filePath, "File not found. path:" + absPath.toString(),
            null);
      } catch (IOException ex) {
        throw new InternalServerErrorException("Can't delete file. path:" + filePath, ex);
      }
    }

    return ServerResponse.ok().contentType(MediaType.APPLICATION_JSON)
        .body(BodyInserters.fromProducer(Mono.just(new DeleteTarget(uid, null, filePathList, false)),
            DeleteTarget.class));
  }

}
