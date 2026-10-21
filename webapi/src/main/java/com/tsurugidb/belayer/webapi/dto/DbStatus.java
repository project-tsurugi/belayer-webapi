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
package com.tsurugidb.belayer.webapi.dto;

import java.util.List;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response body of the DB status API ({@code /api/db/status}), serialized with snake_case keys.
 *
 * <p>{@code status} is one of {@code stop}, {@code starting}, {@code running}, {@code shutdown},
 * {@code disconnected}; {@code mode} is one of {@code standalone}, {@code master}, {@code replica},
 * {@code standby}. {@code instance_name} and {@code tags} come from {@code instance_info.json}.</p>
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class DbStatus {

    private String instanceId;
    private String instanceName;
    private String mode;
    private String status;
    private String modeStatus;
    private String walVersion;
    private String upstream;
    private boolean grpc_server_enabled;
    private String grpc_server_endpoint;
    private List<String> tags;
}
