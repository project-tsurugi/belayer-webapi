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

/**
 * Operation requested on an existing transaction, identified by the type name used in the API path.
 */
public enum TransactionFinishType {
    /** Commit the transaction ({@code commit}). */
    COMMIT("commit"),
    /** Roll back the transaction ({@code rollback}). */
    ROLLBACK("rollback"),
    /** Only show the transaction status ({@code status}). */
    SHOW_STATUS("status");

    private final String typeName;

    private TransactionFinishType(String typeName) {
        this.typeName = typeName;
    }

    /**
     * Returns the type name used in the API path.
     *
     * @return type name ({@code commit}, {@code rollback} or {@code status})
     */
    public String getTypeName() {
        return typeName;
    }

    public String toString() {
        return this.name();
    }

    /**
     * Returns the constant whose type name matches.
     *
     * @param typeName type name ({@code commit}, {@code rollback} or {@code status})
     * @return the matching constant, or {@code null} if none matches
     */
    public static TransactionFinishType fromName(String typeName) {

        for (TransactionFinishType type : values()) {
            if (type.getTypeName().equals(typeName)) {
                return type;
            }
        }
        return null;
    }
}
