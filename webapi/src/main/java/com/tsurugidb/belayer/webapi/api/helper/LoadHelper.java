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
package com.tsurugidb.belayer.webapi.api.helper;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.tsurugidb.belayer.webapi.dto.ColumnMapping;
import com.tsurugidb.belayer.webapi.exception.BadRequestException;
import com.tsurugidb.belayer.webapi.service.FileSystemService;

@Component
public class LoadHelper {

    @Autowired
    private FileSystemService fileSystemService;


    public void checkLoadFiles(String uid, List<String> files) {

        // check file exists
        if (files == null || files.size() == 0) {
            throw new BadRequestException("No dump file is specified.", null);
        }

        for (var filePath : files) {
            fileSystemService.checkFileExists(uid, filePath);
        }
    }

    public void checkLoadMapping(List<ColumnMapping> mappings) {

        // OK if column-mapping is not specified.
        if (mappings == null) {
            return;
        }

        // check column mapping is valid
        for (var mapping : mappings) {
            if (!isValidMappingFormat(mapping.getTargetColumn())) {
                String message = "Invalid mapping definition. target column:" + mapping.getTargetColumn();
                throw new BadRequestException(message, message);
            }

            if (!isValidMappingFormat(mapping.getSourceColumn())) {
                String message = "Invalid mapping definition. source column:" + mapping.getSourceColumn();
                throw new BadRequestException(message, message);
            }
        }
    }

    private boolean isValidMappingFormat(String columnExpression) {
        if (columnExpression.length() == 0) {
            return false;
        }

        if (columnExpression.startsWith("@")) {
            if (columnExpression.length() == 1) {
                return false;
            }
            boolean isNumeric = columnExpression.substring(1).chars().allMatch(Character::isDigit);
            return isNumeric;
        }

        return true;
    }

}
