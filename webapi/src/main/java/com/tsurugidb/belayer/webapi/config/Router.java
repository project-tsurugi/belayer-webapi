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
import static org.springdoc.core.fn.builders.securityrequirement.Builder.securityRequirementBuilder;
import static org.springdoc.webflux.core.fn.SpringdocRouteBuilder.route;
import static com.tsurugidb.belayer.webapi.config.RouterPath.*;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
import com.tsurugidb.belayer.webapi.dto.BackupJob;
import com.tsurugidb.belayer.webapi.dto.BackupRestoreStartRequestBody;
import com.tsurugidb.belayer.webapi.dto.ChangeLauchModeParam;
import com.tsurugidb.belayer.webapi.dto.DbStatus;
import com.tsurugidb.belayer.webapi.dto.DeleteTarget;
import com.tsurugidb.belayer.webapi.dto.DownloadPathList;
import com.tsurugidb.belayer.webapi.dto.DownloadZip;
import com.tsurugidb.belayer.webapi.dto.DumpJob;
import com.tsurugidb.belayer.webapi.dto.DumpJobResult;
import com.tsurugidb.belayer.webapi.dto.DumpRequestBody;
import com.tsurugidb.belayer.webapi.dto.DumpResult;
import com.tsurugidb.belayer.webapi.dto.Endpoints;
import com.tsurugidb.belayer.webapi.dto.ErrorResult;
import com.tsurugidb.belayer.webapi.dto.JobList;
import com.tsurugidb.belayer.webapi.dto.JobResult;
import com.tsurugidb.belayer.webapi.dto.LoadJob;
import com.tsurugidb.belayer.webapi.dto.LoadRequestParam;
import com.tsurugidb.belayer.webapi.dto.LoadResult;
import com.tsurugidb.belayer.webapi.dto.RestoreJob;
import com.tsurugidb.belayer.webapi.dto.SessionStatus;
import com.tsurugidb.belayer.webapi.dto.SessionVariable;
import com.tsurugidb.belayer.webapi.dto.StartDbParam;
import com.tsurugidb.belayer.webapi.dto.StreamDumpRequestBody;
import com.tsurugidb.belayer.webapi.dto.SyncTransactionLogParam;
import com.tsurugidb.belayer.webapi.dto.TableNames;
import com.tsurugidb.belayer.webapi.dto.TransactionStartBody;
import com.tsurugidb.belayer.webapi.dto.TransactionStatus;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import reactor.core.publisher.Mono;

/**
 * Routing configuration for the APIs and the admin page.
 *
 * <p>
 * Each route also carries its OpenAPI description (the {@code *ApiDoc()}
 * methods below). The API information, the security scheme and the schemas
 * without a DTO class are defined in {@link OpenApiConfig}.
 * </p>
 */
@Configuration
public class Router {

  // media types used in the API documentation
  private static final String MEDIA_JSON = "application/json";
  private static final String MEDIA_FORM = "application/x-www-form-urlencoded";
  private static final String MEDIA_MULTIPART = "multipart/form-data";
  private static final String MEDIA_OCTET_STREAM = "application/octet-stream";
  private static final String MEDIA_TEXT = "text/plain";
  private static final String MEDIA_EVENT_STREAM = "text/event-stream";

  // tags that group the operations in the API documentation
  private static final String TAG_AUTH = "auth";
  private static final String TAG_HEALTH = "health";
  private static final String TAG_FS = "fs";
  private static final String TAG_BACKUP_RESTORE = "backup_restore";
  private static final String TAG_DUMP_LOAD = "dump_load";
  private static final String TAG_TRANSACTION = "transaction";
  private static final String TAG_STREAM = "stream";
  private static final String TAG_SESSION = "session";
  private static final String TAG_INSTANCE = "instance";
  private static final String TAG_DB = "db";
  private static final String TAG_ROLE = "role";

  private static final String PATH_ENCODING_NOTE = " Must be URL-encoded: \"/\" in the path is written as \"%2F\".";

  @Value("${belayer.adminpage.enable}")
  boolean adminPageEnabled;

  @Value("${belayer.adminpage.path}")
  String adminPagePath;

  /**
   * Routing definitions for APIs.
   *
   * @param authHandler handler for authentication APIs
   * @param fileSystemApiHandler handler for file system APIs
   * @param backupRestoreApiHandler handler for backup/restore APIs
   * @param dumpLoadApiHandler handler for dump/load APIs
   * @param statefulApiHandler handler for transaction and stream dump/load APIs
   * @param sessionControlApiHandler handler for session control APIs
   * @param endpointsApiHandler handler for endpoint list API
   * @param dbControlHandler handler for database control APIs
   * @param roleUserMappingHandler handler for role-user mapping APIs
   * @param helloHandler handler for health check API
   * @return RouterFunction
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
        .and(route().GET(HEALTH_API.getPath(), helloHandler::hello, helloApiDoc())
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
                backupRestoreDetailApiDoc())
            .build())
        .and(route()
            .GET(LIST_BACKUP_STATUS_API.getPath(), backupRestoreApiHandler::listBackupJob,
                backupRestoreListApiDoc())
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
                dumpLoadDetailApiDoc())
            .build())
        .and(route()
            .GET(LIST_DUMP_LOAD_STATUS_API.getPath(), dumpLoadApiHandler::listJobs,
                dumpLoadListApiDoc())
            .build())
        .and(route()
            .POST(CANCEL_DUMP_LOAD_API.getPath(),
                dumpLoadApiHandler::cancelJob,
                cancelDumpLoadApiDoc())
            .build())
        .and(route()
            .POST(START_TRANSACTION_API.getPath(),
                statefulApiHandler::startTransaction,
                transactionBeginApiDoc())
            .build())
        .and(route()
            .POST(FINISH_TRANSACTION_API.getPath(),
                statefulApiHandler::finishTransaction,
                transactionFinishApiDoc())
            .build())
        .and(route()
            .GET(SHOW_TRANSACTION_STATUS_API.getPath(),
                statefulApiHandler::showTransactionStatus,
                transactionStatusApiDoc())
            .build())
        .and(route()
            .POST(STREAM_DUMP_API.getPath(),
                statefulApiHandler::getDump,
                streamDumpApiDoc())
            .build())
        .and(route()
            .POST(STREAM_LOAD_API.getPath(),
                statefulApiHandler::loadDumpFiles,
                streamLoadApiDoc())
            .build())
        .and(route()
            .GET(SHOW_SESSION_STATUS_API.getPath(),
                sessionControlApiHandler::getStatus,
                sessionStatusApiDoc())
            .build())
        .and(route()
            .POST(SET_SESSION_VAR_API.getPath(),
                sessionControlApiHandler::setVariable,
                sessionSetVariableApiDoc())
            .build())
        .and(route()
            .POST(KILL_SESSION_API.getPath(),
                sessionControlApiHandler::killSession,
                sessionKillApiDoc())
            .build())
        .and(route()
            .GET(ENDPOINTS_API.getPath(),
                endpointsApiHandler::listEndpoints,
                instanceListApiDoc())
            .build())
        .and(route()
            .POST(START_DB_API.getPath(),
                dbControlHandler::startDatabase,
                dbStartApiDoc())
            .build())
        .and(route()
            .POST(CHANGE_DB_MODE_API.getPath(),
                dbControlHandler::changeLaunchMode,
                dbChangeModeApiDoc())
            .build())
        .and(route()
            .POST(SHUTDOWN_DB_API.getPath(),
                dbControlHandler::shutdownDatabase,
                dbShutdownApiDoc())
            .build())
        .and(route()
            .POST(SYNC_TRAN_LOG_API.getPath(),
                dbControlHandler::syncTransactionLog,
                dbSyncApiDoc())
            .build())
        .and(route()
            .GET(SHOW_DB_STATUS_API.getPath(),
                dbControlHandler::getStatus,
                dbStatusApiDoc())
            .build())
        .and(route()
            .GET(LIST_TABLE_NAMES_API.getPath(),
                dbControlHandler::getTableNames,
                tableNamesApiDoc())
            .build())
        .and(route()
            .GET(LIST_ROLES_API.getPath(),
                roleUserMappingHandler::showRoleDefinition,
                roleListApiDoc())
            .build())
        .and(route()
            .GET(SHOW_ROLE_USER_MAPPING_API.getPath(),
                roleUserMappingHandler::showMapping,
                roleUserMappingShowApiDoc())
            .build())
        .and(route()
            .POST(UPDATE_ROLE_USER_MAPPING_API.getPath(),
                roleUserMappingHandler::updateMapping,
                roleUserMappingUpdateApiDoc())
            .build());

    if (adminPageEnabled) {
      route = route.and(RouterFunctions.route(RequestPredicates.GET(adminPagePath), this::adminIndex));
    }

    return route;
  }

  /**
   * ErrorAttributes for this app.
   *
   * @return ErrorAttributes
   */
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

  /**
   * ObjectMapper for this app.
   * Instant values are serialized/deserialized in ISO 8601 format in UTC.
   *
   * @return ObjectMapper
   */
  @Bean
  public ObjectMapper defaultMapper() {
    ObjectMapper objectMapper = new ObjectMapper();
    var module = new JavaTimeModule();
    module.addSerializer(Instant.class, new Iso8601WithMillisInstantSerializer());
    module.addDeserializer(Instant.class, new Iso8601WithMillisInstantDeserializer());
    objectMapper.registerModule(module);
    objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
    return objectMapper;
  }

  /**
   * JSON serializer for Instant, with milliseconds.
   * Format: {@code yyyy-MM-dd'T'HH:mm:ss.SSSxxxxx} in UTC (e.g. {@code 2023-01-02T03:04:05.678+00:00}).
   * Digits below milliseconds are dropped.
   */
  public static class Iso8601WithMillisInstantSerializer extends JsonSerializer<Instant> {
    private DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxxxx")
        .withZone(ZoneOffset.UTC);

    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
      gen.writeString(fmt.format(value));
    }
  }

  /**
   * JSON deserializer for Instant, with milliseconds.
   * Accepts only the format {@code yyyy-MM-dd'T'HH:mm:ss.SSSxxxxx}: exactly three digits of milliseconds and
   * a numeric offset such as {@code +00:00} or {@code +09:00} (e.g. {@code 2023-01-02T03:04:05.678+00:00}).
   * A value without milliseconds, or with {@code Z} as the offset, is rejected.
   */
  public static class Iso8601WithMillisInstantDeserializer extends JsonDeserializer<Instant> {
    private DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSxxxxx")
        .withZone(ZoneOffset.UTC);

    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt)
        throws IOException, JsonProcessingException {
      return Instant.from(fmt.parse(p.getText()));
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers for the API documentation
  // ---------------------------------------------------------------------------

  /**
   * Describes an operation that requires authentication: sets the description
   * (with the permissions required by {@code path}) and the bearer security
   * requirement. Wrap the whole description with {@link #withCommonErrors}.
   */
  private static Builder secured(Builder ops, RouterPath path, String description) {
    String permissions = Stream.of(path.getAuthorities())
        .map(p -> "`" + p.name() + "`")
        .collect(Collectors.joining(", "));
    return ops.description(description + "\n\nRequired permission (any one of): " + permissions + ".")
        .security(securityRequirementBuilder().name(OpenApiConfig.BEARER_AUTH));
  }

  /**
   * Adds the error responses common to all operations that require
   * authentication, after the operation's own responses.
   */
  private static Consumer<Builder> withCommonErrors(Consumer<Builder> doc) {
    return doc.andThen(ops -> ops
        .response(responseBuilder().responseCode("401")
            .description("The access token is missing, invalid or expired (no response body). "
                + "Some operations also return an error body when the token has expired."))
        .response(responseBuilder().responseCode("403")
            .description("The user has none of the required permissions, or the permission is not "
                + "assigned to any role (no response body)."))
        .response(errorResponse("500", "Unexpected error on the server.")));
  }

  /**
   * Describes an operation that does not require authentication.
   */
  private static Builder permitAll(Builder ops, String description) {
    return ops.description(description + "\n\nNo authentication is required.");
  }

  /** 200 response with a JSON body. */
  private static org.springdoc.core.fn.builders.apiresponse.Builder okJson(String description,
      Class<?> implementation) {
    return responseBuilder().responseCode("200").description(description)
        .content(contentBuilder().mediaType(MEDIA_JSON).schema(schemaBuilder().implementation(implementation)));
  }

  /** 200 response with a JSON body that is one of the given classes. */
  private static org.springdoc.core.fn.builders.apiresponse.Builder okJsonOneOf(String description,
      Class<?>... implementations) {
    return responseBuilder().responseCode("200").description(description)
        .content(contentBuilder().mediaType(MEDIA_JSON).schema(schemaBuilder().oneOf(implementations)));
  }

  /** 200 response with a JSON body described by a schema defined in {@link OpenApiConfig}. */
  private static org.springdoc.core.fn.builders.apiresponse.Builder okJsonRef(String description,
      String schemaName) {
    return responseBuilder().responseCode("200").description(description)
        .content(contentBuilder().mediaType(MEDIA_JSON)
            .schema(schemaBuilder().ref(OpenApiConfig.schemaRef(schemaName))));
  }

  /** 200 response without a body. */
  private static org.springdoc.core.fn.builders.apiresponse.Builder okEmpty(String description) {
    return responseBuilder().responseCode("200").description(description);
  }

  /** 200 response with a file (binary) body. */
  private static org.springdoc.core.fn.builders.apiresponse.Builder okFile(String description) {
    return responseBuilder().responseCode("200").description(description)
        .content(contentBuilder().mediaType(MEDIA_OCTET_STREAM)
            .schema(schemaBuilder().type("string").format("binary")));
  }

  /** Error response with an {@link ErrorResult} JSON body. */
  private static org.springdoc.core.fn.builders.apiresponse.Builder errorResponse(String code,
      String description) {
    return responseBuilder().responseCode(code).description(description)
        .content(contentBuilder().mediaType(MEDIA_JSON).schema(schemaBuilder().implementation(ErrorResult.class)));
  }

  /** Required path parameter of type string. */
  private static org.springdoc.core.fn.builders.parameter.Builder pathParam(String name, String description) {
    return parameterBuilder().in(ParameterIn.PATH).name(name).required(true).description(description)
        .schema(schemaBuilder().type("string"));
  }

  /** Required path parameter that takes one of the given values. */
  private static org.springdoc.core.fn.builders.parameter.Builder pathParam(String name, String description,
      String... allowableValues) {
    return parameterBuilder().in(ParameterIn.PATH).name(name).required(true).description(description)
        .schema(schemaBuilder().type("string").allowableValues(allowableValues));
  }

  /** Optional boolean query parameter (default false). */
  private static org.springdoc.core.fn.builders.parameter.Builder queryFlag(String name, String description) {
    return parameterBuilder().in(ParameterIn.QUERY).name(name).required(false).description(description)
        .schema(schemaBuilder().type("boolean").defaultValue("false"));
  }

  /** JSON request body. */
  private static org.springdoc.core.fn.builders.requestbody.Builder jsonBody(Class<?> implementation,
      boolean required, String description) {
    return requestBodyBuilder().required(required).description(description)
        .content(contentBuilder().mediaType(MEDIA_JSON).schema(schemaBuilder().implementation(implementation)));
  }

  /** Request body described by a schema defined in {@link OpenApiConfig}. */
  private static org.springdoc.core.fn.builders.requestbody.Builder refBody(String mediaType, String schemaName,
      String description) {
    return requestBodyBuilder().required(true).description(description)
        .content(contentBuilder().mediaType(mediaType)
            .schema(schemaBuilder().ref(OpenApiConfig.schemaRef(schemaName))));
  }

  // ---------------------------------------------------------------------------
  // API documentation: authentication and health check
  // ---------------------------------------------------------------------------

  /**
   * API Doc for Auth API.
   */
  private Consumer<Builder> authApiDoc() {
    return ops -> permitAll(ops, "Authenticates the user by the user ID (\"uid\") and the password (\"pw\") "
        + "and issues an access token and a refresh token. "
        + "Use the access token in the header \"Authorization: Bearer <access token>\".")
        .tag(TAG_AUTH)
        .operationId("auth")
        .summary("Authenticate by user ID and password.")
        .method("POST")
        .requestBody(requestBodyBuilder().required(true).content(
            contentBuilder()
                .mediaType(MEDIA_FORM)
                .schema(schemaBuilder().implementation(AuthRequest.class))))
        .response(okJson("Authentication succeeded. \"errorMessage\" is null.", AuthResult.class))
        .response(responseBuilder().responseCode("400")
            .description("Authentication failed (user ID or password is blank or wrong). "
                + "The tokens are null and \"errorMessage\" holds the reason.")
            .content(contentBuilder().mediaType(MEDIA_JSON).schema(schemaBuilder().implementation(AuthResult.class))));
  }

  /**
   * API Doc for Token refresh API.
   */
  private Consumer<Builder> authRefreshApiDoc() {
    return ops -> permitAll(ops, "Issues a new access token from the refresh token given as the form parameter \"rt\".")
        .tag(TAG_AUTH)
        .operationId("refresh")
        .summary("Refresh the access token by using the refresh token.")
        .method("POST")
        .requestBody(refBody(MEDIA_FORM, OpenApiConfig.SCHEMA_REFRESH_FORM, "Refresh token."))
        .response(okJson("Refresh succeeded. \"errorMessage\" is null.", AuthResult.class))
        .response(responseBuilder().responseCode("400")
            .description("Refresh failed (the refresh token is blank, invalid or expired). "
                + "The tokens are null and \"errorMessage\" holds the reason.")
            .content(contentBuilder().mediaType(MEDIA_JSON).schema(schemaBuilder().implementation(AuthResult.class))));
  }

  /**
   * API Doc for Hello (health check) API.
   */
  private Consumer<Builder> helloApiDoc() {
    return ops -> permitAll(ops, "Returns the plain text \"hello\". Can be used to check that the server is up.")
        .tag(TAG_HEALTH)
        .operationId("hello")
        .summary("Health check.")
        .method("GET")
        .response(responseBuilder().responseCode("200").description("The server is up.")
            .content(contentBuilder().mediaType(MEDIA_TEXT).schema(schemaBuilder().type("string").example("hello"))));
  }

  // ---------------------------------------------------------------------------
  // API documentation: file system
  // ---------------------------------------------------------------------------

  /**
   * API Doc for Upload API.
   */
  private Consumer<Builder> uploadApiDoc() {
    return withCommonErrors(ops -> secured(ops, UPLOAD_API,
        "Saves the uploaded files into the destination directory (\"destDir\", relative to the user's "
            + "storage directory). The directory is created if it does not exist.")
        .tag(TAG_FS)
        .operationId("upload")
        .summary("Upload files into a directory.")
        .method("POST")
        .requestBody(refBody(MEDIA_MULTIPART, OpenApiConfig.SCHEMA_UPLOAD_FORM, "Files and options."))
        .response(okJson("Upload succeeded. \"fileNames\" holds the download paths of the saved files.",
            DownloadPathList.class))
        .response(errorResponse("400", "The request is not multipart, no file is given, the destination "
            + "directory is invalid, a file with the same name exists (and \"overwrite\" is not true), "
            + "or a directory with the same name exists.")));
  }

  /**
   * API Doc for Download API.
   */
  private Consumer<Builder> downloadApiDoc() {
    return withCommonErrors(ops -> secured(ops, DOWNLOAD_API,
        "Downloads the file at the given path. If the path is a directory, its files are returned as a zip file. "
            + "The response has the header \"Content-Disposition: attachment; filename=<file name>\".")
        .tag(TAG_FS)
        .operationId("download")
        .summary("Download a file.")
        .method("GET")
        .parameter(pathParam("filepath", "File path, relative to the user's storage directory."
            + PATH_ENCODING_NOTE))
        .parameter(queryFlag("csv", "If true, a parquet file is converted to CSV before download "
            + "(file name: <base name>_yyyyMMddHHmmssSSS.csv)."))
        .response(okFile("The file content."))
        .response(errorResponse("400", "The path is outside the user's storage directory, the file or "
            + "directory does not exist, or \"csv\" is true and the file is not a parquet file.")));
  }

  /**
   * API Doc for DownloadZip API.
   */
  private Consumer<Builder> downloadZipApiDoc() {
    return withCommonErrors(ops -> secured(ops, DOWNLOADZIP_API,
        "Downloads the given files as one zip file named belayer_download_yyyyMMddHHmmssSSS.zip "
            + "(UTC time of the request). All files must be in the same directory. "
            + "The response has the header \"Content-Disposition: attachment; filename=<file name>\". "
            + "\"uid\" in the request body is ignored (the authenticated user is used).")
        .tag(TAG_FS)
        .operationId("downloadzip")
        .summary("Download multiple files as a zip file.")
        .method("POST")
        .parameter(queryFlag("csv", "If true, parquet files are converted to CSV before they are zipped."))
        .requestBody(jsonBody(DownloadZip.class, true,
            "\"pathList\": file paths relative to the user's storage directory."))
        .response(okFile("The zip file content."))
        .response(errorResponse("400", "No path is given, the files are not in the same directory, "
            + "a path is outside the user's storage directory or does not exist, or the body is not valid JSON.")));
  }

  /**
   * API Doc for List Files API.
   */
  private Consumer<Builder> fileListApiDoc() {
    return withCommonErrors(ops -> secured(ops, LIST_FILES_API,
        "Lists the files and directories under the given directory (recursively). "
            + "Paths are relative to the user's storage directory and directories end with \"/\". "
            + "If the number of entries exceeds the limit (webapi.list.max.files), the list is truncated "
            + "and \"message\" tells the actual number.")
        .tag(TAG_FS)
        .operationId("filelist")
        .summary("List files in a directory.")
        .method("GET")
        .parameter(pathParam("dirpath", "Directory path, relative to the user's storage directory. "
            + "\".\" for the storage directory itself." + PATH_ENCODING_NOTE))
        .parameter(queryFlag("hide_file", "If true, only directories are listed. "
            + "Cannot be combined with hide_dir."))
        .parameter(queryFlag("hide_dir", "If true, only files are listed. Cannot be combined with hide_file."))
        .response(okJson("List of the paths.", DownloadPathList.class))
        .response(errorResponse("400", "Both hide_file and hide_dir are true, or the path is outside "
            + "the user's storage directory."))
        .response(errorResponse("404", "The directory does not exist.")));
  }

  /**
   * API Doc for Delete File API.
   */
  private Consumer<Builder> deleteFileApiDoc() {
    return withCommonErrors(ops -> secured(ops, DELETE_FILE_API,
        "Deletes one file. Only \"path\" in the request body is used. "
            + "The response returns the deleted path in \"pathList\" (\"path\" is null).")
        .tag(TAG_FS)
        .operationId("delete_file")
        .summary("Delete a file.")
        .method("POST")
        .requestBody(jsonBody(DeleteTarget.class, true,
            "\"path\": file path relative to the user's storage directory."))
        .response(okJson("Deletion succeeded.", DeleteTarget.class))
        .response(errorResponse("400", "No path is given, the path is outside the user's storage directory, "
            + "or the body is not valid JSON."))
        .response(errorResponse("404", "The file does not exist, or the path is a directory.")));
  }

  /**
   * API Doc for Delete Files API.
   */
  private Consumer<Builder> deleteFilesApiDoc() {
    return withCommonErrors(ops -> secured(ops, DELETE_FILES_API,
        "Deletes the given files in order. Only \"pathList\" in the request body is used. "
            + "Processing stops at the first error; files before it have already been deleted.")
        .tag(TAG_FS)
        .operationId("delete_files")
        .summary("Delete multiple files.")
        .method("POST")
        .requestBody(jsonBody(DeleteTarget.class, true,
            "\"pathList\": file paths relative to the user's storage directory."))
        .response(okJson("Deletion succeeded. \"pathList\" holds the deleted paths.", DeleteTarget.class))
        .response(errorResponse("400", "No \"pathList\" is given or it contains null, a path is outside "
            + "the user's storage directory, or the body is not valid JSON."))
        .response(errorResponse("404", "A file does not exist, or a path is a directory.")));
  }

  /**
   * API Doc for Delete Directory API.
   */
  private Consumer<Builder> deleteDirApiDoc() {
    return withCommonErrors(ops -> secured(ops, DELETE_DIR_API,
        "Deletes a directory. Only \"path\" and \"force\" in the request body are used. "
            + "Without \"force\": true, the directory must be empty.")
        .tag(TAG_FS)
        .operationId("delete_dir")
        .summary("Delete a directory.")
        .method("POST")
        .requestBody(jsonBody(DeleteTarget.class, true,
            "\"path\": directory path relative to the user's storage directory. "
                + "\"force\": true to delete the directory with its content (default false)."))
        .response(okJson("Deletion succeeded. \"path\" holds the deleted directory.", DeleteTarget.class))
        .response(errorResponse("400", "No path is given, the path is outside the user's storage directory, "
            + "the directory is not empty (without \"force\"), or the body is not valid JSON."))
        .response(errorResponse("404", "The directory does not exist, or the path is a file.")));
  }

  // ---------------------------------------------------------------------------
  // API documentation: backup and restore
  // ---------------------------------------------------------------------------

  /**
   * API Doc for Backup Start API.
   */
  private Consumer<Builder> backupStartApiDoc() {
    return withCommonErrors(ops -> secured(ops, BACKUP_START_API,
        "Starts a backup job and returns without waiting for it. The backup file (zip) is saved under "
            + "\"dirPath\" (relative to the user's storage directory). Only \"dirPath\" in the request body "
            + "is used. Online or offline backup is chosen by the database status. "
            + "Check the progress with /api/br/status/backup/{jobid}.")
        .tag(TAG_BACKUP_RESTORE)
        .operationId("backup_start")
        .summary("Start a backup.")
        .method("POST")
        .requestBody(jsonBody(BackupRestoreStartRequestBody.class, true,
            "\"dirPath\": directory to save the backup file in."))
        .response(okJson("The backup job is started.", JobResult.class))
        .response(errorResponse("400", "\"dirPath\" is empty or outside the user's storage directory, "
            + "or the body is not valid JSON.")));
  }

  /**
   * API Doc for Restore Start API.
   */
  private Consumer<Builder> restoreStartApiDoc() {
    return withCommonErrors(ops -> secured(ops, RESTORE_START_API,
        "Starts a restore job from a backup file (zip) and returns without waiting for it. "
            + "Only \"zipFilePath\" in the request body is used. The database must be offline. "
            + "Check the progress with /api/br/status/restore/{jobid}.")
        .tag(TAG_BACKUP_RESTORE)
        .operationId("restore_start")
        .summary("Start a restore.")
        .method("POST")
        .requestBody(jsonBody(BackupRestoreStartRequestBody.class, true,
            "\"zipFilePath\": backup file path, relative to the user's storage directory."))
        .response(okJson("The restore job is started.", JobResult.class))
        .response(errorResponse("400", "\"zipFilePath\" is empty or is not a file, the database is online, "
            + "or the body is not valid JSON.")));
  }

  /**
   * API Doc for Backup/Restore Status API.
   */
  private Consumer<Builder> backupRestoreDetailApiDoc() {
    return withCommonErrors(ops -> secured(ops, BACKUP_STATUS_API,
        "Returns the backup or restore job of the user with the given job ID.")
        .tag(TAG_BACKUP_RESTORE)
        .operationId("backup_job_detail")
        .summary("Get a backup/restore job.")
        .method("GET")
        .parameter(pathParam("type", "Job type.", "backup", "restore"))
        .parameter(pathParam("jobid", "Job ID."))
        .response(okJsonOneOf("The job (a backup job or a restore job, by \"type\").",
            BackupJob.class, RestoreJob.class))
        .response(errorResponse("400", "Invalid type."))
        .response(errorResponse("404", "The job is not found.")));
  }

  /**
   * API Doc for List Backup/Restore API.
   */
  private Consumer<Builder> backupRestoreListApiDoc() {
    return withCommonErrors(ops -> secured(ops, LIST_BACKUP_STATUS_API,
        "Returns the backup or restore jobs of the user. "
            + "Each element of \"jobList\" is a backup job or a restore job (see backup_job_detail).")
        .tag(TAG_BACKUP_RESTORE)
        .operationId("backup_job_list")
        .summary("List backup/restore jobs.")
        .method("GET")
        .parameter(pathParam("type", "Job type.", "backup", "restore"))
        .response(okJson("List of the jobs.", JobList.class))
        .response(errorResponse("400", "Invalid type.")));
  }

  /**
   * API Doc for Cancel Backup/Restore API.
   */
  private Consumer<Builder> cancelBackupRestoreApiDoc() {
    return withCommonErrors(ops -> secured(ops, CANCEL_BACKUP_RESTORE_API,
        "Cancels a running backup or restore job of the user.")
        .tag(TAG_BACKUP_RESTORE)
        .operationId("cancel_backup_restore_job")
        .summary("Cancel a backup/restore job.")
        .method("POST")
        .parameter(pathParam("type", "Job type.", "backup", "restore"))
        .parameter(pathParam("jobid", "Job ID."))
        .response(okJsonOneOf("The canceled job (status CANCELED).", BackupJob.class, RestoreJob.class))
        .response(errorResponse("400", "Invalid type, or the job is not running."))
        .response(errorResponse("404", "The job is not found.")));
  }

  // ---------------------------------------------------------------------------
  // API documentation: dump and load
  // ---------------------------------------------------------------------------

  /**
   * API Doc for Dump Start API.
   */
  private Consumer<Builder> dumpStartApiDoc() {
    return withCommonErrors(ops -> secured(ops, DUMP_START_API,
        "Starts a dump job of the table. The dump files are saved under \"dirPath\" "
            + "(relative to the user's storage directory). Without \"waitUntilDone\", returns without "
            + "waiting for the job and \"files\", \"status\" and \"errorMessage\" of the response are null; "
            + "with \"waitUntilDone\": true, returns when the job finishes and they are set. "
            + "Check the progress with /api/dumpload/status/dump/{jobid}.")
        .tag(TAG_DUMP_LOAD)
        .operationId("dump_start")
        .summary("Start a dump of a table.")
        .method("POST")
        .parameter(pathParam("table", "Table name."))
        .requestBody(jsonBody(DumpRequestBody.class, true,
            "\"dirPath\": output directory (required). \"format\": \"csv\" or \"parquet\" (default parquet). "
                + "\"waitUntilDone\": wait for the job to finish (default false)."))
        .response(okJson("The dump job is started (or finished, with waitUntilDone).", DumpJobResult.class))
        .response(errorResponse("400", "\"dirPath\" is empty or outside the user's storage directory, "
            + "or the body is not valid JSON.")));
  }

  /**
   * API Doc for Load Start API.
   */
  private Consumer<Builder> loadStartApiDoc() {
    return withCommonErrors(ops -> secured(ops, LOAD_START_API,
        "Starts a load job that loads data files on the server into the table. Rows with the same primary key "
            + "are overwritten. The response holds \"jobId\", \"uid\" and \"type\" only (\"files\", \"status\" "
            + "and \"errorMessage\" are always null). Fields of the request body other than those listed "
            + "below are ignored. Check the progress with /api/dumpload/status/load/{jobid}.")
        .tag(TAG_DUMP_LOAD)
        .operationId("load_start")
        .summary("Start a load into a table.")
        .method("POST")
        .parameter(pathParam("table", "Table name."))
        .requestBody(jsonBody(LoadRequestParam.class, true,
            "\"files\": data file paths relative to the user's storage directory (one or more, required). "
                + "\"format\": \"parquet\", \"csv\", \"zip\" or \"detect_by_ext\" (default, by extension). "
                + "\"transactional\": load in one transaction (default true). "
                + "\"waitUntilDone\": wait for the job to finish (default false). "
                + "\"mappings\": column mappings; each column is a name or \"@N\" (column number)."))
        .response(okJson("The load job is started.", DumpJobResult.class))
        .response(errorResponse("400", "The body is empty or not valid JSON, no file is given, a file "
            + "does not exist, or a column mapping is invalid.")));
  }

  /**
   * API Doc for Dump/Load Status API.
   */
  private Consumer<Builder> dumpLoadDetailApiDoc() {
    return withCommonErrors(ops -> secured(ops, DUMP_LOAD_STATUS_API,
        "Returns the dump or load job of the user with the given job ID.")
        .tag(TAG_DUMP_LOAD)
        .operationId("dump_load_job_detail")
        .summary("Get a dump/load job.")
        .method("GET")
        .parameter(pathParam("type", "Job type.", "dump", "load"))
        .parameter(pathParam("jobid", "Job ID."))
        .response(okJsonOneOf("The job (a dump job or a load job, by \"type\").", DumpJob.class, LoadJob.class))
        .response(errorResponse("400", "Invalid type."))
        .response(errorResponse("404", "The job is not found.")));
  }

  /**
   * API Doc for List Dump/Load API.
   */
  private Consumer<Builder> dumpLoadListApiDoc() {
    return withCommonErrors(ops -> secured(ops, LIST_DUMP_LOAD_STATUS_API,
        "Returns the dump or load jobs of the user. "
            + "Each element of \"jobList\" is a dump job or a load job (see dump_load_job_detail).")
        .tag(TAG_DUMP_LOAD)
        .operationId("dump_load_job_list")
        .summary("List dump/load jobs.")
        .method("GET")
        .parameter(pathParam("type", "Job type.", "dump", "load"))
        .response(okJson("List of the jobs.", JobList.class))
        .response(errorResponse("400", "Invalid type.")));
  }

  /**
   * API Doc for Cancel Dump/Load API.
   */
  private Consumer<Builder> cancelDumpLoadApiDoc() {
    return withCommonErrors(ops -> secured(ops, CANCEL_DUMP_LOAD_API,
        "Cancels a running dump or load job of the user.")
        .tag(TAG_DUMP_LOAD)
        .operationId("cancel_dump_load_job")
        .summary("Cancel a dump/load job.")
        .method("POST")
        .parameter(pathParam("type", "Job type.", "dump", "load"))
        .parameter(pathParam("jobid", "Job ID."))
        .response(okJsonOneOf("The canceled job (status CANCELED).", DumpJob.class, LoadJob.class))
        .response(errorResponse("400", "Invalid type, or the job is not running."))
        .response(errorResponse("404", "The job is not found.")));
  }

  // ---------------------------------------------------------------------------
  // API documentation: transaction (Stream API)
  // ---------------------------------------------------------------------------

  /**
   * API Doc for Transaction Begin API.
   */
  private Consumer<Builder> transactionBeginApiDoc() {
    return withCommonErrors(ops -> secured(ops, START_TRANSACTION_API,
        "Begins a transaction for the stream dump/load APIs. The transaction is kept alive until it is "
            + "committed or rolled back, or until it times out. The response status is AVAILABLE.")
        .tag(TAG_TRANSACTION)
        .operationId("transaction_begin")
        .summary("Begin a transaction.")
        .method("POST")
        .requestBody(jsonBody(TransactionStartBody.class, true,
            "\"type\": \"read_only\" or \"read_write\" (required). \"timeoutMin\": timeout in minutes, "
                + "as a string of an integer (required). \"tables\": tables to write in (for read_write)."))
        .response(okJson("The transaction has begun.", TransactionStatus.class))
        .response(errorResponse("400", "Invalid \"type\" or \"timeoutMin\", or the body is not valid JSON.")));
  }

  /**
   * API Doc for Transaction Commit/Rollback API.
   */
  private Consumer<Builder> transactionFinishApiDoc() {
    return withCommonErrors(ops -> secured(ops, FINISH_TRANSACTION_API,
        "Commits or rolls back the transaction. Commit is allowed only in status AVAILABLE, rollback in "
            + "AVAILABLE or IN_USE. The resulting status is COMMITED or ROLLBACK_COMPLETED.")
        .tag(TAG_TRANSACTION)
        .operationId("transaction_finish")
        .summary("Commit or roll back a transaction.")
        .method("POST")
        .parameter(pathParam("type", "Operation.", "commit", "rollback"))
        .parameter(pathParam("transaction_id", "Transaction ID."))
        .response(okJson("The transaction is committed or rolled back.", TransactionStatus.class))
        .response(errorResponse("400", "Invalid type, or the status of the transaction does not allow it."))
        .response(errorResponse("404", "The transaction is not found.")));
  }

  /**
   * API Doc for Transaction Status API.
   */
  private Consumer<Builder> transactionStatusApiDoc() {
    return withCommonErrors(ops -> secured(ops, SHOW_TRANSACTION_STATUS_API,
        "Returns the status of the transaction: AVAILABLE, IN_USE (dump or load running), COMMITED, "
            + "ROLLBACK_COMPLETED or FAILED.")
        .tag(TAG_TRANSACTION)
        .operationId("transaction_status")
        .summary("Get the status of a transaction.")
        .method("GET")
        .parameter(pathParam("transaction_id", "Transaction ID."))
        .response(okJson("The transaction status.", TransactionStatus.class))
        .response(errorResponse("404", "The transaction is not found.")));
  }

  // ---------------------------------------------------------------------------
  // API documentation: stream dump and load (Stream API)
  // ---------------------------------------------------------------------------

  /**
   * API Doc for Stream Dump API.
   */
  private Consumer<Builder> streamDumpApiDoc() {
    return withCommonErrors(ops -> secured(ops, STREAM_DUMP_API,
        "Dumps the table within the transaction (status AVAILABLE or IN_USE) into the user's storage "
            + "directory and returns the download paths. With \"mode\": \"normal\" (default) the paths are "
            + "returned together as JSON when the dump finishes; with any other mode (e.g. \"sse\") they are "
            + "sent one by one as Server-Sent Events: \"table_name=...\", \"transaction_id=...\", "
            + "\"format=...\", then \"download_path=...\" per file (\"/\" encoded as \"%2F\").")
        .tag(TAG_STREAM)
        .operationId("stream_dump")
        .summary("Dump a table within a transaction.")
        .method("POST")
        .parameter(pathParam("transaction_id", "Transaction ID."))
        .parameter(pathParam("table_name", "Table name."))
        .requestBody(jsonBody(StreamDumpRequestBody.class, false,
            "\"format\": \"csv\", otherwise parquet (default). \"mode\": \"normal\" (default) or \"sse\"."))
        .response(responseBuilder().responseCode("200")
            .description("The dump files (JSON for mode normal, Server-Sent Events otherwise).")
            .content(contentBuilder().mediaType(MEDIA_JSON).schema(schemaBuilder().implementation(DumpResult.class)))
            .content(contentBuilder().mediaType(MEDIA_EVENT_STREAM).schema(schemaBuilder().type("string"))))
        .response(errorResponse("400", "The status of the transaction does not allow a dump, "
            + "or the body is not valid JSON."))
        .response(errorResponse("404", "The transaction is not found.")));
  }

  /**
   * API Doc for Stream Load API.
   */
  private Consumer<Builder> streamLoadApiDoc() {
    return withCommonErrors(ops -> secured(ops, STREAM_LOAD_API,
        "Uploads data files and loads them into the table within the transaction (status AVAILABLE or IN_USE). "
            + "Rows with the same primary key are overwritten. The files are stored temporarily in the "
            + "directory named by the transaction ID and deleted after the load. "
            + "Errors after the request is accepted as multipart (unknown transaction, invalid column mapping, "
            + "load failure) are all returned as 500.")
        .tag(TAG_STREAM)
        .operationId("stream_load")
        .summary("Load data files into a table within a transaction.")
        .method("POST")
        .parameter(pathParam("transaction_id", "Transaction ID."))
        .parameter(pathParam("table_name", "Table name."))
        .requestBody(refBody(MEDIA_MULTIPART, OpenApiConfig.SCHEMA_STREAM_LOAD_FORM,
            "Data files, column mappings and format."))
        .response(okJson("The files are loaded.", LoadResult.class))
        .response(errorResponse("400", "The request is not multipart.")));
  }

  // ---------------------------------------------------------------------------
  // API documentation: session control
  // ---------------------------------------------------------------------------

  /**
   * API Doc for Session Status API.
   */
  private Consumer<Builder> sessionStatusApiDoc() {
    return withCommonErrors(ops -> secured(ops, SHOW_SESSION_STATUS_API,
        "Returns whether the TsurugiDB session is available (\"status\": \"available\" or \"unavailable\").")
        .tag(TAG_SESSION)
        .operationId("session_status")
        .summary("Get the status of a session.")
        .method("GET")
        .parameter(pathParam("session_id", "Session ID."))
        .response(okJson("The session status (\"session_id\" and \"status\").", SessionStatus.class)));
  }

  /**
   * API Doc for Session Variable Setting API.
   */
  private Consumer<Builder> sessionSetVariableApiDoc() {
    return withCommonErrors(ops -> secured(ops, SET_SESSION_VAR_API,
        "Sets a variable of the TsurugiDB session.")
        .tag(TAG_SESSION)
        .operationId("session_set_variable")
        .summary("Set a session variable.")
        .method("POST")
        .requestBody(jsonBody(SessionVariable.class, true,
            "\"session_id\", \"var_name\" and \"var_value\" (all required)."))
        .response(okJson("The variable is set (\"session_id\" and \"var_name\").", SessionStatus.class))
        .response(errorResponse("400", "A parameter is missing, the variable could not be set "
            + "(including an unavailable session), or the body is not valid JSON.")));
  }

  /**
   * API Doc for Session Kill API.
   */
  private Consumer<Builder> sessionKillApiDoc() {
    return withCommonErrors(ops -> secured(ops, KILL_SESSION_API,
        "Kills the TsurugiDB session. Only \"session_id\" in the request body is used.")
        .tag(TAG_SESSION)
        .operationId("session_kill")
        .summary("Kill a session.")
        .method("POST")
        .requestBody(jsonBody(SessionStatus.class, true, "\"session_id\" (required)."))
        .response(okEmpty("The session is killed (no response body)."))
        .response(errorResponse("400", "\"session_id\" is missing, the session could not be killed, "
            + "or the body is not valid JSON.")));
  }

  // ---------------------------------------------------------------------------
  // API documentation: instance
  // ---------------------------------------------------------------------------

  /**
   * API Doc for Endpoint List API.
   */
  private Consumer<Builder> instanceListApiDoc() {
    return withCommonErrors(ops -> secured(ops, ENDPOINTS_API,
        "Returns the Belayer endpoints listed in endpoints.csv in the config directory (BELAYER_CONFIG_ROOT). "
            + "If the file does not exist, is empty or cannot be read, returns the base URL of this request.")
        .tag(TAG_INSTANCE)
        .operationId("instance_list")
        .summary("List the Belayer endpoints.")
        .method("GET")
        .response(okJson("List of the endpoints.", Endpoints.class)));
  }

  // ---------------------------------------------------------------------------
  // API documentation: database control
  // ---------------------------------------------------------------------------

  /**
   * API Doc for Database Start API.
   */
  private Consumer<Builder> dbStartApiDoc() {
    return withCommonErrors(ops -> secured(ops, START_DB_API,
        "Starts TsurugiDB without waiting for it to be up. Only the modes listed in "
            + "webapi.tsurugi.supported.modes are accepted.")
        .tag(TAG_DB)
        .operationId("db_start")
        .summary("Start the database.")
        .method("POST")
        .requestBody(jsonBody(StartDbParam.class, false,
            "\"mode\": launch mode (default standalone). \"replicateFrom\": upstream endpoint "
                + "(required for replica). \"autoFetchWal\": \"true\" or \"false\" (default true)."))
        .response(okEmpty("The start command is accepted (no response body)."))
        .response(errorResponse("400", "\"replicateFrom\" is missing for replica, the mode is not supported, "
            + "the command failed, or the body is not valid JSON.")));
  }

  /**
   * API Doc for Database Mode Change API.
   */
  private Consumer<Builder> dbChangeModeApiDoc() {
    return withCommonErrors(ops -> secured(ops, CHANGE_DB_MODE_API,
        "Changes the launch mode of TsurugiDB without waiting for it to finish. Only the modes listed in "
            + "webapi.tsurugi.supported.modes are accepted.")
        .tag(TAG_DB)
        .operationId("db_change_mode")
        .summary("Change the database mode.")
        .method("POST")
        .requestBody(jsonBody(ChangeLauchModeParam.class, true,
            "\"mode\": launch mode (required). \"replicateFrom\": upstream endpoint (required for replica). "
                + "\"autoFetchWal\": \"true\" or \"false\" (default true)."))
        .response(okEmpty("The mode change command is accepted (no response body)."))
        .response(errorResponse("400", "\"mode\" is missing, \"replicateFrom\" is missing for replica, "
            + "the mode is not supported, the command failed, or the body is not valid JSON.")));
  }

  /**
   * API Doc for Database Shutdown API.
   */
  private Consumer<Builder> dbShutdownApiDoc() {
    return withCommonErrors(ops -> secured(ops, SHUTDOWN_DB_API,
        "Shuts down TsurugiDB without waiting for it to stop.")
        .tag(TAG_DB)
        .operationId("db_shutdown")
        .summary("Shut down the database.")
        .method("POST")
        .response(okEmpty("The shutdown command is accepted (no response body)."))
        .response(errorResponse("400", "The command failed.")));
  }

  /**
   * API Doc for Database Sync API.
   */
  private Consumer<Builder> dbSyncApiDoc() {
    return withCommonErrors(ops -> secured(ops, SYNC_TRAN_LOG_API,
        "Instructs TsurugiDB to synchronize its WAL from the given source.")
        .tag(TAG_DB)
        .operationId("db_sync")
        .summary("Synchronize the WAL of the database.")
        .method("POST")
        .requestBody(jsonBody(SyncTransactionLogParam.class, true,
            "\"from\": source endpoint (required; must not contain a double quote or whitespace)."))
        .response(okEmpty("The sync command is accepted (no response body)."))
        .response(errorResponse("400", "\"from\" is missing or invalid, the command failed, "
            + "or the body is not valid JSON.")));
  }

  /**
   * API Doc for Database Status API.
   */
  private Consumer<Builder> dbStatusApiDoc() {
    return withCommonErrors(ops -> secured(ops, SHOW_DB_STATUS_API,
        "Returns the status of TsurugiDB. \"instance_name\" and \"tags\" come from instance_info.json "
            + "in the config directory. \"status\" is one of stop, starting, running, shutdown and disconnected.")
        .tag(TAG_DB)
        .operationId("db_status")
        .summary("Get the database status.")
        .method("GET")
        .response(okJson("The database status.", DbStatus.class)));
  }

  /**
   * API Doc for Table Name List API.
   */
  private Consumer<Builder> tableNamesApiDoc() {
    return withCommonErrors(ops -> secured(ops, LIST_TABLE_NAMES_API,
        "Returns the names of the tables in TsurugiDB that the user can access.")
        .tag(TAG_DB)
        .operationId("db_table_names")
        .summary("List table names.")
        .method("GET")
        .response(okJson("List of the table names.", TableNames.class)));
  }

  // ---------------------------------------------------------------------------
  // API documentation: roles
  // ---------------------------------------------------------------------------

  /**
   * API Doc for Role Definition API.
   */
  private Consumer<Builder> roleListApiDoc() {
    return withCommonErrors(ops -> secured(ops, LIST_ROLES_API,
        "Returns the roles and the permissions granted to each role.")
        .tag(TAG_ROLE)
        .operationId("role_list")
        .summary("Get the role definitions.")
        .method("GET")
        .response(okJsonRef("Role names mapped to permissions.", OpenApiConfig.SCHEMA_ROLE_DEFINITION)));
  }

  /**
   * API Doc for Role-User Mapping Show API.
   */
  private Consumer<Builder> roleUserMappingShowApiDoc() {
    return withCommonErrors(ops -> secured(ops, SHOW_ROLE_USER_MAPPING_API,
        "Returns the role-user mapping.")
        .tag(TAG_ROLE)
        .operationId("role_user_mapping_show")
        .summary("Get the role-user mapping.")
        .method("GET")
        .response(okJsonRef("Role names mapped to user IDs.", OpenApiConfig.SCHEMA_ROLE_USER_MAPPING)));
  }

  /**
   * API Doc for Role-User Mapping Update API.
   */
  private Consumer<Builder> roleUserMappingUpdateApiDoc() {
    return withCommonErrors(ops -> secured(ops, UPDATE_ROLE_USER_MAPPING_API,
        "Replaces the whole role-user mapping with the given one and saves it. "
            + "The body is read as JSON regardless of its Content-Type.")
        .tag(TAG_ROLE)
        .operationId("role_user_mapping_update")
        .summary("Update the role-user mapping.")
        .method("POST")
        .requestBody(refBody(MEDIA_JSON, OpenApiConfig.SCHEMA_ROLE_USER_MAPPING, "New role-user mapping."))
        .response(responseBuilder().responseCode("200").description("The mapping is updated.")
            .content(contentBuilder().mediaType(MEDIA_JSON)
                .schema(schemaBuilder().type("string").example("Success\n"))))
        .response(errorResponse("400", "The mapping is invalid.")));
  }
}
