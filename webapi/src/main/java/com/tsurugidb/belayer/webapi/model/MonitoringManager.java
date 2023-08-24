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
import java.nio.file.ClosedWatchServiceException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MonitoringManager {

    private final WatchService watchService;

    @Autowired
    ThreadPoolTaskExecutor threadPoolTaskExecutor;

    private Map<Path, Consumer<Path>> listeners = new ConcurrentHashMap<>();

    private AtomicBoolean stopping = new AtomicBoolean(false);

    public MonitoringManager() throws IOException {
        watchService = FileSystems.getDefault().newWatchService();
    }

    public void addFileWatcher(FileWatcher fileWatcher) throws IOException {
        Objects.requireNonNull(fileWatcher);

        fileWatcher.setMonitoringManager(this);

        Path targetFile = fileWatcher.getTargetFilePath();
        Path targetDir = targetFile.getParent();
        Path fileName = targetDir.relativize(targetFile);

        if (!Files.isDirectory(targetDir)) {
            throw new IllegalArgumentException("incorrect monitoring folder: " + targetDir);
        }

        log.debug("Add watch target dir:" + targetDir);
        var consumer = fileWatcher.getConsumer();
        if (consumer != null) {
            listeners.put(fileName, consumer);
        }

        WatchKey key = targetDir.register(
                watchService,
                StandardWatchEventKinds.ENTRY_CREATE,
                StandardWatchEventKinds.ENTRY_MODIFY);

        fileWatcher.setWatchKey(key);
        log.debug("monitor :{}", fileName);
    }

    public void removeFileWatcher(FileWatcher fileWatcher) {
        var consumer = fileWatcher.getConsumer();
        if (consumer != null) {
            listeners.entrySet().removeIf(entry -> entry.getValue() == consumer);
        }
    }

    @PostConstruct
    public void lannch() {
        threadPoolTaskExecutor.execute(() -> launchMonitoring());
    }

    void launchMonitoring() {
        log.info("START_MONITORING");
        try {
            WatchKey key;
            while ((key = watchService.take()) != null) {
                for (WatchEvent<?> event : key.pollEvents()) {
                    log.debug("Event kind: {}; File affected: {}", event.kind(), event.context());
                    var changed = (Path) event.context();
                    var listener = listeners.get(changed);
                    if (listener != null) {
                        listener.accept(changed);
                    }
                }
                key.reset();
            }
        } catch (InterruptedException ex) {
            log.info("interrupted exception for monitoring service", ex);
        } catch (ClosedWatchServiceException ex) {
            if (!stopping.get()) {
                log.error("Watch Service is now closed.", ex);
            }
        }
    }

    @PreDestroy
    public void stopMonitoring() {

        log.info("STOP_MONITORING");
        stopping.set(true);

        if (watchService != null) {
            try {
                watchService.close();
            } catch (IOException e) {
                log.error("exception while closing the monitoring service");
            }
        }
    }

    public interface MonitoringListener {
        WatchEvent<?> listenEvent();
    }
}
