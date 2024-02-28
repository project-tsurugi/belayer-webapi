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
package com.tsurugidb.belayer.webapi.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Predicate;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tsurugidb.belayer.webapi.dto.ExecStatus;
import com.tsurugidb.belayer.webapi.exception.IORuntimeException;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class FileWatcher {

    private final AtomicLong lineCounter = new AtomicLong(0);

    private WatchKey watchKey;

    private Path targetFilePath;

    private ExecStatus execStatus;

    private Consumer<ExecStatus> callback;

    private Consumer<Path> consumer = this::consumeLines;

    private MonitoringManager monitoringManager;

    public FileWatcher(Path targetFile) {
        this.targetFilePath = targetFile;
    }

    public ExecStatus waitForExecStatus(Predicate<ExecStatus> p) {
        int count = 0;
        while (true) {
            count++;
            synchronized (this) {
                if (p.test(this.execStatus) || count > 10) {
                    return this.execStatus;
                }
            }
            try {
                Thread.sleep(100L);
            } catch (InterruptedException ex) {
                // ignore
            }
        }

    }

    public ExecStatus waitForExecStatus() {

        return waitForExecStatus(status -> status != null);
    }

    public void close() {
        log.debug("close watcher");
        if (watchKey != null) {
            watchKey.cancel();
        }
        monitoringManager.removeFileWatcher(this);
    }

    private void consumeLines(Path filePath) {
        try {
            // note: use absolute path instead of file path.
            Files.lines(targetFilePath)
                    .skip(lineCounter.get())
                    .forEach(line -> {
                        log.debug("monitor file line:" + line);
                        synchronized (this) {
                            if (this.execStatus == null || !this.execStatus.isFreezed()) {
                                this.execStatus = parseLine(line);
                                lineCounter.incrementAndGet();
                                if (callback != null) {
                                    callback.accept(execStatus);
                                }
                            }
                        }
                    });
        } catch (IOException ex) {
            throw new IORuntimeException("I/O Exception occurred while file watching.", ex);
        }
    }

    ExecStatus parseLine(String line) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            ExecStatus status = objectMapper.readValue(line, ExecStatus.class);

            return status;
        } catch (JsonProcessingException ex) {
            throw new IORuntimeException("Failed to parse JSON line", ex);
        }
    }
}
