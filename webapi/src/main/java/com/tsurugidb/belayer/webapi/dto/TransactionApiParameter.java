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

import lombok.Data;
import lombok.ToString;

/**
 * Parameters of the transaction APIs (begin, commit/rollback, status).
 *
 * <p>Holds the authenticated user ({@code uid}, {@code credentials}), the {@code transactionId},
 * and, depending on the API, the {@code transactionMode}, the timeout in minutes ({@code timeoutMin}),
 * the tables to write ({@code tables}) or the {@code finishType}.</p>
 */
@Data
public class TransactionApiParameter {

    private String uid;
    @ToString.Exclude
    private String credentials;
    private int timeoutMin;
    private String transactionId;
    private TransactionMode transactionMode;
    private TransactionFinishType finishType;
    private String[] tables;
    
}
