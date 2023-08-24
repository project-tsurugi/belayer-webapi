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
package com.tsurugidb.belayer.webapi.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Customized ResponseStatusException
 */
public class BelayerResponseStatusException extends ResponseStatusException {

    private String displayMessage;

    /**
     * Constructor
     *
     * @param status Http response status code
     * @param displayMessage message for display
     * @param reason exception reason to log
     * @param cause cause of exception
     */
    public BelayerResponseStatusException(HttpStatus status, String displayMessage, String reason, Throwable cause) {
        super(status, reason, cause);
        this.displayMessage = displayMessage;
    }

    /**
     * return display message
     * 
     * @return display message
     */
    public String getDisplayMessage() {
        return displayMessage;
    }
    
}
