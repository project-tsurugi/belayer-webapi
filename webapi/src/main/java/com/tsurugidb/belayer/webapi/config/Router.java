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
package com.tsurugidb.belayer.webapi.config;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static org.springdoc.core.fn.builders.schema.Builder.schemaBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;
import static com.tsurugidb.belayer.webapi.config.RouterPath.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;

import org.springdoc.core.fn.builders.operation.Builder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.reactive.error.ErrorAttributes;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tsurugidb.belayer.webapi.api.AuthHandler;
import com.tsurugidb.belayer.webapi.api.BackupRestoreApiHandler;
import com.tsurugidb.belayer.webapi.api.DbControlApiHandler;
import com.tsurugidb.belayer.webapi.api.DumpLoadApiHandler;
import com.tsurugidb.belayer.webapi.api.EndpointsApiHandler;
import com.tsurugidb.belayer.webapi.api.FileSystemApiHandler;
import com.tsurugidb.belayer.webapi.api.HelloHandler;
import com.tsurugidb.belayer.webapi.api.RoleUserMappingHandler;
import com.tsurugidb.belayer.webapi.api.SessionControlApiHandler;
import com.tsurugidb.belayer.webapi.api.StatefulApiHandler;
import com.tsurugidb.belayer.webapi.dto.AuthRequest;
import com.tsurugidb.belayer.webapi.dto.AuthResult;
import com.tsurugidb.belayer.webapi.dto.BackupRestoreStartRequestBody;
import com.tsurugidb.belayer.webapi.dto.DeleteTarget;
import com.tsurugidb.belayer.webapi.dto.DownloadPathList;
import com.tsurugidb.belayer.webapi.dto.DownloadZip;
import com.tsurugidb.belayer.webapi.dto.Job;
import com.tsurugidb.belayer.webapi.dto.JobList;
import com.tsurugidb.belayer.webapi.dto.JobResult;
import com.tsurugidb.belayer.webapi.dto.LoadRequestParam;
import com.tsurugidb.belayer.webapi.dto.StreamDumpRequestBody;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import reactor.core.publisher.Mono;

@Configuration
public class Router {

  @Value("${belayer.adminpage.enable}")
  boolean adminPageEnabled;

  @Value("${belayer.adminpage.path}")
  String adminPagePath;

  /**
   * Routing definitions for APIs.
   */
  @Bean
  public RouterFunction<ServerResponse> routerFunction(AuthHandler authHandler,
      FileSystemApiHandler fileSystemApiHandler, BackupRestoreApiHandler backupRestoreApiHandler,
      DumpLoadApiHandler dumpLoadApiHandler, StatefulApiHandler statefulApiHandler,
      SessionControlApiHandler sessionControlApiHandler,
      EndpointsApiHandler endpointsApiHandler,
      DbControlApiHandler dbControlHandler, RoleUserMappingHandler roleUserMappingHandler,
      HelloHandler helloHandler) {

    RouterFunction<ServerResponse> route = route().POST(AUTH_API.getPath(), authHandler::auth, authApiDoc()).build()
        .and(route().POST(AUTH_REFRESH_API.getPath(), authHandler::refresh, authRefreshApiDoc()).build())
        .and(route().GET(HEALTH_API.getPath(), helloHandler::hello, opt -> opt.operationId("hello").build())
            .build())
        .and(route().POST(UPLOAD_API.getPath(), fileSystemApiHandler::uploadFiles, uploadApiDoc())
            .build())
        .and(route()
            .GET(DOWNLOAD_API.getPath(), fileSystemApiHandler::downloadFile, downloadApiDoc())
            .build())
        .and(route()
            .POST(DOWNLOADZIP_API.getPath(), fileSystemApiHandler::downloadzipFile, downloadZipApiDoc())
            .build())
        .and(route()
            .GET(LIST_FILES_API.getPath(), fileSystemApiHandler::listFiles, fileListApiDoc())
            .build())
        .and(route()
            .POST(DELETE_FILE_API.getPath(), fileSystemApiHandler::deleteFile,
                deleteFileApiDoc())
            .build())
        .and(route()
            .POST(DELETE_FILES_API.getPath(), fileSystemApiHandler::deleteFiles, deleteFilesApiDoc())
            .build())
        .and(route()
            .POST(DELETE_DIR_API.getPath(), fileSystemApiHandler::deleteDir, deleteDirApiDoc())
            .build())
        .and(route()
            .POST(BACKUP_START_API.getPath(), backupRestoreApiHandler::requestBackup,
                backupStartApiDoc())
            .build())
        .and(route()
            .POST(RESTORE_START_API.getPath(), backupRestoreApiHandler::requestRestore,
                restoreStartApiDoc())
            .build())
        .and(route()
            .GET(BACKUP_STATUS_API.getPath(),
                backupRestoreApiHandler::showBackupJobDetail,
                showDetailApiDoc())
            .build())
        .and(route()
            .GET(LIST_BACKUP_STATUS_API.getPath(), backupRestoreApiHandler::listBackupJob,
                listJobApiDoc())
            .build())
        .and(route()
            .POST(CANCEL_BACKUP_RESTORE_API.getPath(),
                backupRestoreApiHandler::cancelJob,
                cancelBackupRestoreApiDoc())
            .build())
        .and(route()
            .POST(DUMP_START_API.getPath(), dumpLoadApiHandler::requestDump,
                dumpStartApiDoc())
            .build())
        .and(route()
            .POST(LOAD_START_API.getPath(), dumpLoadApiHandler::requestLoad,
                loadStartApiDoc())
            .build())
        .and(route()
            .GET(DUMP_LOAD_STATUS_API.getPath(),
                dumpLoadApiHandler::showJobDetail,
                showDetailApiDoc())
            .build())
        .and(route()
            .GET(LIST_DUMP_LOAD_STATUS_API.getPath(), dumpLoadApiHandler::listJobs,
                listJobApiDoc())
            .build())
        .and(route()
            .POST(CANCEL_DUMP_LOAD_API.getPath(),
                dumpLoadApiHandler::cancelJob,
                cancelDumpLoadApiDoc())
            .build())
        .and(route()
            .POST(START_TRANSACTION_API.getPath(),
                statefulApiHandler::startTransaction,
                opt -> opt.operationId("stateful").build())
            .build())
        .and(route()
            .POST(FINISH_TRANSACTION_API.getPath(),
                statefulApiHandler::finishTransaction,
                opt -> opt.operationId("stateful").build())
            .build())
        .and(route()
            .GET(SHOW_TRANSACTION_STATUS_API.getPath(),
                statefulApiHandler::showTransactionStatus,
                opt -> opt.operationId("stateful").build())
            .build())
        .and(route()
            .POST(STREAM_DUMP_API.getPath(),
                statefulApiHandler::getDump,
                opt -> opt.operationId("stateful").build())
            .build())
        .and(route()
            .POST(STREAM_LOAD_API.getPath(),
                statefulApiHandler::loadDumpFiles,
                opt -> opt.operationId("stateful").build())
            .build())
        .and(route()
            .GET(SHOW_SESSION_STATUS_API.getPath(),
                sessionControlApiHandler::getStatus,
                opt -> opt.operationId("session").build())
            .build())
        .and(route()
            .POST(SET_SESSION_VAR_API.getPath(),
                sessionControlApiHandler::setVariable,
                opt -> opt.operationId("session").build())
            .build())
        .and(route()
            .POST(KILL_SESSION_API.getPath(),
                sessionControlApiHandler::killSession,
                opt -> opt.operationId("session").build())
            .build())
        .and(route()
            .GET(ENDPOINTS_API.getPath(),
                endpointsApiHandler::listEndpoints,
                opt -> opt.operationId("instance").build())
            .build())
        .and(route()
            .POST(START_DB_API.getPath(),
                dbControlHandler::startDatabase,
                opt -> opt.operationId("db").build())
            .build())
        .and(route()
            .POST(SHUTDOWN_DB_API.getPath(),
                dbControlHandler::shutdownDatabase,
                opt -> opt.operationId("db").build())
            .build())
        .and(route()
            .POST(SYNC_TRAN_LOG_API.getPath(),
                dbControlHandler::syncTransactionLog,
                opt -> opt.operationId("db").build())
            .build())
        .and(route()
            .GET(SHOW_DB_STATUS_API.getPath(),
                dbControlHandler::getStatus,
                opt -> opt.operationId("db").build())
            .build())
        .and(route()
            .GET(LIST_TABLE_NAMES_API.getPath(),
                dbControlHandler::getTableNames,
                opt -> opt.operationId("db").build())
            .build())
        .and(route()
            .GET(LIST_ROLES_API.getPath(),
                roleUserMappingHandler::showRoleDefinition,
                opt -> opt.operationId("role").build())
            .build())
        .and(route()
            .GET(SHOW_ROLE_USER_MAPPING_API.getPath(),
                roleUserMappingHandler::showMapping,
                opt -> opt.operationId("role").build())
            .build())
        .and(route()
            .POST(UPDATE_ROLE_USER_MAPPING_API.getPath(),
                roleUserMappingHandler::updateMapping,
                opt -> opt.operationId("role").build())
            .build());

    if (adminPageEnabled) {
      route = route.and(RouterFunctions.route(RequestPredicates.GET(adminPagePath), this::adminIndex));
    }

    return route;
  }

  @Bean
  public ErrorAttributes globalErrorAttributes() {
    return new GlobalErrorAttributes();
  }

  /**
   * Redirect /admin to admin welcome page
   *
   * @param req Request
   * @return Response
   */
  public Mono<ServerResponse> adminIndex(ServerRequest req) {
    try {
      return ServerResponse.temporaryRedirect(new URI(adminPagePath + "/index.html")).build();
    } catch (URISyntaxException ex) {
      throw new IllegalArgumentException(ex);
    }
  }

  @Bean
  public ObjectMapper defaultMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    var module = new JavaTimeModule();
    module.addSerializer(Instant.class, new Iso8601WithoutMillisInstantSerializer());
    module.addDeserializer(Instant.class, new Iso8601WithoutMillisInstantDeserializer());
    objectMapper.registerModule(module);
    objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    return objectMapper;
  }

  public class Iso8601WithoutMillisInstantSerializer extends JsonSerializer<Instant> {
    private DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxxxx")
        .withZone(ZoneOffset.UTC);

    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      gen.writeString(fmt.format(value));
    }
  }

  public class Iso8601WithoutMillisInstantDeserializer extends JsonDeserializer<Instant> {
    private DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxxxx")
        .withZone(ZoneOffset.UTC);

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException, JsonProcessingException {
      return Instant.from(fmt.parse(p.getText()));
    }
  }

  /**
   * API Doc for Auth API.
   */
  private Consumer<Builder> authApiDoc() {
    return ops -> ops.tag("auth")
        .operationId("auth")
        .summary("auth by user Id and password.")
        .method("POST")
        .requestBody(requestBodyBuilder().content(
            contentBuilder()
                .mediaType("application/x-www-form-urlencoded")
                .schema(schemaBuilder().type("object").implementation(AuthRequest.class))))
        .response(responseBuilder().responseCode("200").description("Authentication is succeeded.")
            .implementation(AuthResult.class))
        .response(responseBuilder().responseCode("400").description("Authentication is failed."));
  }

  /**
   * API Doc for Token refresh API.
   */
  private Consumer<Builder> authRefreshApiDoc() {
    return ops -> ops.tag("auth")
        .operationId("refresh")
        .summary("refresh auth token by using refresh token.")
        .method("POST")
        .requestBody(requestBodyBuilder().content(
            contentBuilder()
                .mediaType("application/x-www-form-urlencoded")
                .schema(schemaBuilder().type("object").implementation(String.class))))
        .response(responseBuilder().responseCode("200").description("Refresh is succeeded.")
            .implementation(AuthResult.class))
        .response(responseBuilder().responseCode("400").description("Refresh is failed."));
  }

  /**
   * API Doc for Upload API.
   */
  private Consumer<Builder> uploadApiDoc() {
    return ops -> ops.tag("fs")
        .operationId("upload")
        .summary("upload files into destination directory.")
        .method("POST")
        .parameter(parameterBuilder().in(ParameterIn.PATH).name("destDir")
            .description("destination directory path."))
        .requestBody(requestBodyBuilder().content(contentBuilder().mediaType("multi-part/form-data")))
        .response(responseBuilder().responseCode("200").description("Upload Succeeded.")
            .implementation(DownloadPathList.class))
        .response(responseBuilder().responseCode("400").description("Invalid directory or file path."));
  }

  /**
   * API Doc for DownloadZip API.
   */
  private Consumer<Builder> downloadZipApiDoc() {
    return ops -> ops.tag("fs")
        .operationId("downloadzip")
        .summary("Downloads multiple file paths.")
        .method("POST")
        .requestBody(requestBodyBuilder().content(
            contentBuilder()
                .mediaType("application/json")
                .schema(schemaBuilder().type("object").implementation(DownloadZip.class))))
        .response(responseBuilder().responseCode("200").description("Upload Succeeded.")
            .content(contentBuilder().mediaType("application/octed-stream")))
        .response(responseBuilder().responseCode("404").description("Specified file is not found."))
        .response(responseBuilder().responseCode("400").description("Invalid directory or file path."));

  }

  /**
   * API Doc for Download API.
   */
  private Consumer<Builder> downloadApiDoc() {
    return ops -> ops.tag("fs")
        .operationId("download")
        .summary("download a file specified file path.")
        .method("GET")
        .parameter(
            parameterBuilder().in(ParameterIn.PATH).name("filepath").description("file path to download."))
        .response(responseBuilder().responseCode("200").description("Upload Succeeded.")
            .content(contentBuilder().mediaType("application/octed-stream")))
        .response(responseBuilder().responseCode("404").description("Specified file is not found."))
        .response(responseBuilder().responseCode("400").description("Invalid directory or file path."));

  }

  /**
   * API Doc for List Files API.
   */
  private Consumer<Builder> fileListApiDoc() {
    return ops -> ops.tag("fs")
        .operationId("filelist")
        .summary("list files in specified directory path.")
        .method("GET")
        .parameter(
            parameterBuilder().in(ParameterIn.PATH).name("dirpath").description("directory path to list."))
        .response(responseBuilder().responseCode("200").description("List Succeeded.")
            .content(contentBuilder().mediaType("application/json"))
            .implementation(DownloadPathList.class));
  }

  /**
   * API Doc for Delete File API.
   */
  private Consumer<Builder> deleteFileApiDoc() {
    return ops -> ops.tag("fs")
        .operationId("filelist")
        .summary("delete a file in specified directory path.")
        .method("POST")
        .parameter(
            parameterBuilder().in(ParameterIn.PATH).name("filepath").description("file path to delete."))
        .response(responseBuilder().responseCode("200").description("Deletion Succeeded.")
            .content(contentBuilder().mediaType("application/json"))
            .implementation(DeleteTarget.class));
  }

  /**
   * API Doc for Delete File API.
   */
  private Consumer<Builder> deleteFilesApiDoc() {
    return ops -> ops.tag("fs")
        .operationId("filelist")
        .summary("delete a file in specified directory path.")
        .method("POST")
        .requestBody(requestBodyBuilder().content(
            contentBuilder()
                .mediaType("application/json")
                .schema(schemaBuilder().type("object").implementation(DeleteTarget.class))))
        .response(responseBuilder().responseCode("200").description("Deletion Succeeded.")
            .content(contentBuilder().mediaType("application/json"))
            .implementation(DeleteTarget.class));
  }

  /**
   * API Doc for Delete Directory API.
   */
  private Consumer<Builder> deleteDirApiDoc() {
    return ops -> ops.tag("fs")
        .operationId("filelist")
        .summary("delete a file in specified directory path.")
        .method("POST")
        .parameter(
            parameterBuilder().in(ParameterIn.PATH).name("dirpath")
                .description("directory path to delete."))
        .response(responseBuilder().responseCode("200").description("Deletion Succeeded.")
            .content(contentBuilder().mediaType("application/json"))
            .implementation(DeleteTarget.class));
  }

  /**
   * API Doc for Backup Start API.
   */
  private Consumer<Builder> backupStartApiDoc() {
    return ops -> ops.tag("backup_restore")
        .operationId("backup_start")
        .summary("start backup and save backup files in specified directory path.")
        .method("POST")
        .requestBody(requestBodyBuilder().content(
            contentBuilder()
                .mediaType("application/json")
                .schema(schemaBuilder().type("object")
                    .implementation(BackupRestoreStartRequestBody.class))))
        .response(responseBuilder().responseCode("200").description("Back up execution Succeeded.")
            .content(contentBuilder().mediaType("application/json"))
            .implementation(JobResult.class))
        .response(responseBuilder().responseCode("400").description("Invalid directory path specified."));
  }

  /**
   * API Doc for Restore Start API.
   */
  private Consumer<Builder> restoreStartApiDoc() {
    return ops -> ops.tag("backup_restore")
        .operationId("restore_start")
        .summary("start restore with a directory that holds backup files in specified directory path.")
        .method("POST")
        .requestBody(requestBodyBuilder().content(
            contentBuilder()
                .mediaType("application/json")
                .schema(schemaBuilder().type("object")
                    .implementation(BackupRestoreStartRequestBody.class))))
        .response(responseBuilder().responseCode("200").description("Restore execution Succeeded.")
            .content(contentBuilder().mediaType("application/json"))
            .implementation(JobResult.class))
        .response(responseBuilder().responseCode("400").description("Invalid directory path specified."));
  }

  /**
   * API Doc for Backup Status API.
   */
  private Consumer<Builder> showDetailApiDoc() {
    return ops -> ops.tag("backup_restore")
        .operationId("backup_job_detail")
        .summary("get backup job detail or restore job detail.")
        .method("GET")
        .parameter(parameterBuilder().in(ParameterIn.PATH).name("jobid").description("job id to check status."))
        .parameter(
            parameterBuilder().in(ParameterIn.PATH).name("type")
                .description("Job type. \"backup\" or \"restore\"."))
        .response(responseBuilder().responseCode("200")
            .description("Return detail of Backup job or Restore job.")
            .content(contentBuilder().mediaType("application/json")).implementation(Job.class))
        .response(responseBuilder().responseCode("404")
            .description("Invalid Job ID. Specfied Job is not found."));
  }

  /**
   * API Doc for List Backup/Restore API.
   */
  private Consumer<Builder> listJobApiDoc() {
    return ops -> ops.tag("backup_restore")
        .operationId("backup_job_list")
        .summary("get backup status or restore status..")
        .method("GET")
        .parameter(
            parameterBuilder().in(ParameterIn.PATH).name("type")
                .description("Job type. \"backup\" or \"restore\"."))
        .response(responseBuilder().responseCode("200").description("Return list of Backup job or Restore job.")
            .content(contentBuilder().mediaType("application/json"))
            .implementation(JobList.class));
  }

  /**
   * API Doc for Cancel Backup API.
   */
  private Consumer<Builder> cancelBackupRestoreApiDoc() {
    return ops -> ops.tag("backup_restore")
        .operationId("cancel_backup_restore_job")
        .summary("cancel backup/restore job.")
        .method("POST")
        .parameter(parameterBuilder().in(ParameterIn.PATH).name("jobid").description("job id to check status."))
        .parameter(
            parameterBuilder().in(ParameterIn.PATH).name("type")
                .description("Job type. \"backup\" or \"restore\"."))
        .response(responseBuilder().responseCode("200").description("Return canceled Backup/Restore job.")
            .content(contentBuilder().mediaType("application/json")).implementation(Job.class))
        .response(
            responseBuilder().responseCode("404").description("Invalid Job ID. Specfied Job is not found."))
        .response(responseBuilder().responseCode("400").description("Invalid Job status."));
  }

  /**
   * API Doc for Dump Start API.
   */
  private Consumer<Builder> dumpStartApiDoc() {
    return ops -> ops.tag("dump_load")
        .operationId("dump_start")
        .summary("start dump and save dump files in specified directory path.")
        .method("POST")
        .parameter(parameterBuilder().in(ParameterIn.PATH).name("table")
            .description("table name to dump."))
        .requestBody(requestBodyBuilder().content(
            contentBuilder()
                .mediaType("application/json")
                .schema(schemaBuilder().type("object").implementation(StreamDumpRequestBody.class))))
        .response(responseBuilder().responseCode("200").description("Back up execution Succeeded.")
            .content(contentBuilder().mediaType("application/json"))
            .implementation(JobResult.class))
        .response(responseBuilder().responseCode("400").description("Invalid directory path specified."));
  }

  /**
   * API Doc for Load Start API.
   */
  private Consumer<Builder> loadStartApiDoc() {
    return ops -> ops.tag("dump_load")
        .operationId("load_start")
        .summary("start load dump files in specified path.")
        .method("POST")
        .parameter(parameterBuilder().in(ParameterIn.PATH).name("table")
            .description("table name to load."))
        .requestBody(requestBodyBuilder().content(
            contentBuilder()
                .mediaType("application/json")
                .schema(schemaBuilder().type("object").implementation(LoadRequestParam.class))))
        .response(responseBuilder().responseCode("200").description("Back up execution Succeeded.")
            .content(contentBuilder().mediaType("application/json"))
            .implementation(JobResult.class))
        .response(responseBuilder().responseCode("406").description("DB is now offline."));
  }

  /**
   * API Doc for Cancel Backup API.
   */
  private Consumer<Builder> cancelDumpLoadApiDoc() {
    return ops -> ops.tag("dump_load")
        .operationId("cancel_dump_load_job")
        .summary("cancel dump/load job.")
        .method("POST")
        .parameter(parameterBuilder().in(ParameterIn.PATH).name("jobid").description("job id to check status."))
        .parameter(
            parameterBuilder().in(ParameterIn.PATH).name("type")
                .description("Job type. \"dump\" or \"load\"."))
        .response(responseBuilder().responseCode("200").description("Return canceled Backup/Restore job.")
            .content(contentBuilder().mediaType("application/json")).implementation(Job.class))
        .response(
            responseBuilder().responseCode("404").description("Invalid Job ID. Specfied Job is not found."))
        .response(responseBuilder().responseCode("400").description("Invalid Job status."));
  }
}
