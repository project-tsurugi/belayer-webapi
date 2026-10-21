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

/**
 * One record of a monitoring file written by an external command (e.g. {@code tgctl_w} / {@code tgha_w}),
 * deserialized from a JSON line with snake_case keys.
 *
 * <p>{@code kind} tells the record type ({@code KIND_*}); {@code status} is one of the
 * {@code STATUS_*} values; {@code timestamp} is in epoch seconds; {@code progress} is a ratio
 * (1 means 100%). The session fields ({@code session_id}, {@code label}, ...) are filled by
 * {@code data} records of the session status command.</p>
 */
@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ExecStatus {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_DATE_TIME;

    /** Record kind: the command started. */
    public static final String KIND_START = "start";
    /** Record kind: the command finished; {@code status} holds the result. */
    public static final String KIND_FINISH = "finish";
    /** Record kind: progress report. */
    public static final String KIND_PROGRESS = "progress";
    /** Record kind: data output by the command. */
    public static final String KIND_DATA = "data";

    /** Database status: not running. */
    public static final String STATUS_STOP = "stop";
    /** Database status: starting up. */
    public static final String STATUS_STARTING = "starting";
    /** Database status: running. */
    public static final String STATUS_RUNNING = "running";
    /** Database status: shutting down. */
    public static final String STATUS_SHUTDOWN = "shutdown";
    /** Database status: was running but cannot be connected. */
    public static final String STATUS_DISCONNECTED = "disconnected";
    /** Command result: succeeded. */
    public static final String STATUS_SUCCESS = "success";
    /** Command result: failed. */
    public static final String STATUS_FAILURE = "failure";

    private Long timestamp;
    private String kind;
    private String status;
    private String format;
    private String reason;
    private BigDecimal progress = new BigDecimal(0, new MathContext(2, RoundingMode.HALF_UP));
    private String message;
    private String code;
    private String cause;
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

    /**
     * Returns a one-line human-readable summary of this record, used in logs and error messages.
     *
     * @return summary string depending on {@code kind}
     */
    public String toStatusString() {

        switch (kind) {
            case KIND_START:
                return String.format("[%s]", toTimestampString(), kind);
            case KIND_FINISH:
                return String.format("[%s]status:%s,reason=%s,cause=%s,message=%s,code=%s,arguments=%s",
                        toTimestampString(), kind, status, reason, cause, message, code, arguments);
            case KIND_PROGRESS:
                return String.format("[%s]%s:%s", toTimestampString(), kind, toPercentage(progress));
            default:
                return "status unknown: [%s]" + toString();
        }
    }

    private String toTimestampString() {
        if (this.timestamp == null) {
            return "";
        }

        return LocalDateTime.ofEpochSecond(this.timestamp, 0, ZoneOffset.UTC).format(DATE_FORMAT);
    }

    private String toPercentage(BigDecimal progress) {

        return new DecimalFormat("##0%").format(progress);
    }

}
