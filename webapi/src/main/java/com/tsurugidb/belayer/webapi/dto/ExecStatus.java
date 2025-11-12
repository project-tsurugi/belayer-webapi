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

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ExecStatus {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_DATE_TIME;

    public static final String KIND_START = "start";
    public static final String KIND_FINISH = "finish";
    public static final String KIND_PROGRESS = "progress";
    public static final String KIND_DATA = "data";

    public static final String STATUS_STOP = "stop";
    public static final String STATUS_STARTING = "starting";
    public static final String STATUS_RUNNING = "running";
    public static final String STATUS_SHUTDOWN = "shutdown";
    public static final String STATUS_DISCONNECTED = "disconnected";
    public static final String STATUS_SUCCESS = "success";
    public static final String STATUS_FAILURE = "failure";

    private Long timestamp;
    private String kind;
    private String status;
    private String format;
    private String reason;
    private BigDecimal progress = new BigDecimal(0, new MathContext(2, RoundingMode.HALF_UP));
    private String message;
    private String code;
    private String[] arguments;

    private String level;
    private String sessionId;
    private String label;
    private String application;
    private String user;
    private String startAt;
    private String connectionType;
    private String connectionInfo;

    private boolean freezed = false;

    public String toStatusString() {

        switch (kind) {
            case KIND_START:
                return String.format("[%s]%s", toTimestampString(), kind);
            case KIND_FINISH:
                return String.format("[%s]%s:%s:%s", toTimestampString(), kind, status, reason);
            case KIND_PROGRESS:
                return String.format("[%s]%s:%s", toTimestampString(), kind, toPercentage(progress));
            default:
                return "status unknown: [%s]" + toString();
        }
    }

    private String toTimestampString() {
        if (this.timestamp == null) {
            return "?";
        }

        return LocalDateTime.ofEpochSecond(this.timestamp, 0, ZoneOffset.UTC).format(DATE_FORMAT);
    }

    private String toPercentage(BigDecimal progress) {

        return new DecimalFormat("##0%").format(progress);
    }

}
