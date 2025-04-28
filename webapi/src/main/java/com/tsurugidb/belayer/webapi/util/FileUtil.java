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
package com.tsurugidb.belayer.webapi.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import com.tsurugidb.belayer.webapi.exception.InternalServerErrorException;

public class FileUtil {

    /**
     * get file size
     * 
     * @param path target file.
     * @return file size
     * @throws InternalServerErrorException Occurs when file size cannot be read
     */
    public static long getFileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            throw new InternalServerErrorException("Failed to read file size.", e);
        }
    }
    
}
