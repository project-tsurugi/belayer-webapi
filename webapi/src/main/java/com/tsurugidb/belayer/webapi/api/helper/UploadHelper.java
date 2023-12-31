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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.ServerRequest;

import com.tsurugidb.belayer.webapi.exception.BadRequestException;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

@Component
@Slf4j
public class UploadHelper {

    @Data
    public static class UploadParameter {
        private String destDirPath;
        private String uid;
        private Object credentials;
        private boolean overwrite;
        private List<FilePart> fileParts = new ArrayList<FilePart>();

        public void addFilePart(FilePart filePart) {
            fileParts.add(filePart);
        }
    }

    public void checkRequestHeader(ServerRequest request) {
        var contentType = request.headers().contentType();
        log.debug("contentType:{}", contentType);
        if (contentType.isEmpty() || !"multipart".equals(contentType.get().getType())) {
            var msg = "request is not multipart.";
            throw new BadRequestException(msg, msg);
        }
    }

    /**
     * Save uploaded file.
     * 
     * @param uid       UID
     * @param dirPath   absolute dir path to place file.
     * @param filePart  FilePart.
     * @return absolute path of a uploaded file.
     */
    public Mono<String> saveFile(String uid, Path dirPath, FilePart filePart) {

        final String filePath = filePart.filename();
        // get fileName without dir path.
        int index = filePath.lastIndexOf("/");
        String fileName = index > 0 ? filePath.substring(index + 1) : filePath;

        Path fullPath = Path.of(dirPath.toString(), fileName).toAbsolutePath().normalize();

        log.debug("save file: " + fullPath);

        // check resource is dir
        if (Files.isDirectory(fullPath)) {
            throw new BadRequestException(
                    "Directory already exisits.",
                    "Directory already exisits. file:" + fullPath, null);
        }

        Mono<Void> saveFile = filePart.transferTo(fullPath);

        return saveFile.then(Mono.just(fullPath.toString()));
    }

}
