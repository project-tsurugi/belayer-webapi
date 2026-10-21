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

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.BinarySchema;
import io.swagger.v3.oas.models.media.BooleanSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityScheme;

/**
 * OpenAPI (springdoc) settings shared by all operations: API information, the
 * bearer authentication scheme, and the schemas of request/response bodies
 * that have no DTO class (multipart forms, form parameters and role maps).
 *
 * <p>
 * The operations themselves are described in {@link Router}.
 * </p>
 */
@Configuration
public class OpenApiConfig {

  /** Name of the security scheme for the bearer access token. */
  public static final String BEARER_AUTH = "bearerAuth";

  /** Schema name of the multipart form of the upload API. */
  public static final String SCHEMA_UPLOAD_FORM = "UploadForm";

  /** Schema name of the multipart form of the stream load API. */
  public static final String SCHEMA_STREAM_LOAD_FORM = "StreamLoadForm";

  /** Schema name of the form parameters of the token refresh API. */
  public static final String SCHEMA_REFRESH_FORM = "RefreshForm";

  /** Schema name of the role-to-users mapping. */
  public static final String SCHEMA_ROLE_USER_MAPPING = "RoleUserMapping";

  /** Schema name of the role-to-permissions definition. */
  public static final String SCHEMA_ROLE_DEFINITION = "RoleDefinition";

  private static final String SCHEMA_REF_PREFIX = "#/components/schemas/";

  /** Version used when neither the property nor the jar manifest gives one. */
  private static final String UNKNOWN_VERSION = "unspecified";

  @Value("${belayer.openapi.version:}")
  String apiVersion;

  /**
   * Returns the JSON reference to a schema defined in this class.
   *
   * @param schemaName schema name (one of the {@code SCHEMA_*} constants)
   * @return reference such as {@code #/components/schemas/UploadForm}
   */
  public static String schemaRef(String schemaName) {
    return SCHEMA_REF_PREFIX + schemaName;
  }

  /**
   * OpenAPI definition with the API information, the security scheme and the
   * shared schemas.
   *
   * @return OpenAPI definition
   */
  @Bean
  public OpenAPI belayerOpenApi() {
    return new OpenAPI()
        .info(new Info()
            .title("Belayer Web API")
            .description("REST API of the Belayer server. "
                + "Except for /api/auth, /api/refresh and /api/hello, every API requires an access token "
                + "obtained from /api/auth in the header \"Authorization: Bearer <access token>\", "
                + "and the user must have one of the permissions listed in the description of each operation.")
            .version(resolveVersion())
            .license(new License().name("Apache License, Version 2.0")
                .url("http://www.apache.org/licenses/LICENSE-2.0")))
        .components(new Components()
            .addSecuritySchemes(BEARER_AUTH, new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("Access token issued by /api/auth (or /api/refresh)."))
            .addSchemas(SCHEMA_UPLOAD_FORM, uploadFormSchema())
            .addSchemas(SCHEMA_STREAM_LOAD_FORM, streamLoadFormSchema())
            .addSchemas(SCHEMA_REFRESH_FORM, refreshFormSchema())
            .addSchemas(SCHEMA_ROLE_USER_MAPPING, stringListMapSchema(
                "Role names mapped to the user IDs (regular expressions are allowed) that have the role.",
                Map.of("ROLE_ADMIN", List.of("tsurugi"), "ROLE_BACKUP", List.of("backup_.*"))))
            .addSchemas(SCHEMA_ROLE_DEFINITION, stringListMapSchema(
                "Role names mapped to the permissions granted to the role.",
                Map.of("ROLE_BACKUP", List.of("P_BACKUP", "P_FILE_DIR_DELETE", "P_FILE_LIST", "P_DOWNLOAD")))));
  }

  private String resolveVersion() {
    if (apiVersion != null && !apiVersion.isBlank()) {
      return apiVersion;
    }
    String implementationVersion = OpenApiConfig.class.getPackage().getImplementationVersion();
    if (implementationVersion != null && !implementationVersion.isBlank()) {
      return implementationVersion;
    }
    return UNKNOWN_VERSION;
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private static Schema uploadFormSchema() {
    return new ObjectSchema()
        .description("Multipart form of the upload API.")
        .addProperty("file", new ArraySchema().items(new BinarySchema())
            .description("Files to upload (part name \"file\", one part per file). "
                + "The file name of each part is used as the name of the saved file "
                + "(any directory part of it is removed)."))
        .addProperty("destDir", new StringSchema()
            .description("Destination directory, relative to the user's storage directory. "
                + "Created if it does not exist."))
        .addProperty("overwrite", new BooleanSchema()._default(false)
            .description("\"true\" to overwrite existing files. Otherwise an existing file is an error."))
        .required(List.of("file", "destDir"));
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private static Schema streamLoadFormSchema() {
    return new ObjectSchema()
        .description("Multipart form of the stream load API.")
        .addProperty("file", new ArraySchema().items(new BinarySchema())
            .description("Data files to load (part name \"file\", one part per file)."))
        .addProperty("col-map", new ArraySchema().items(new StringSchema().example("COL_A,@1"))
            .description("Column mappings, one part per mapping, each as \"<source column>,<target column>\". "
                + "A column can be given by name or as \"@N\" (column number). "
                + "A value without a comma is ignored."))
        .addProperty("format", new StringSchema()
            ._enum(List.of("parquet", "csv", "zip", "detect_by_ext"))
            ._default("detect_by_ext")
            .description("Format of the data files. \"detect_by_ext\" decides it by the file extension."))
        .required(List.of("file"));
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private static Schema refreshFormSchema() {
    return new ObjectSchema()
        .description("Form parameters of the token refresh API.")
        .addProperty("rt", new StringSchema().description("Refresh token issued by /api/auth."))
        .required(List.of("rt"));
  }

  @SuppressWarnings({ "rawtypes", "unchecked" })
  private static Schema stringListMapSchema(String description, Map<String, List<String>> example) {
    return new MapSchema()
        .additionalProperties(new ArraySchema().items(new StringSchema()))
        .description(description)
        .example(example);
  }
}
