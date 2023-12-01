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

import java.io.IOException;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.tsurugidb.belayer.webapi.util.FileUtil;
import com.tsurugidb.tsubakuro.common.Session;
import com.tsurugidb.tsubakuro.datastore.Backup;
import com.tsurugidb.tsubakuro.datastore.DatastoreClient;
import com.tsurugidb.tsubakuro.exception.ServerException;

import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@Slf4j
public class BackupTransaction implements AutoCloseable {

    private String jobId;
    private Session session;
    private DatastoreClient datastoreClient;
    private Backup backup;

    /**
     * return Path of files to backup.
     * 
     * @param job                               Backup Job
     * @param progressPercentageWhenApiRetern   progress percentage(0~100)
     * @param progressPercentageWhenSumComputed progress percentage(0~100)
     * @return Path of files and current session.
     */
    public List<BackupContext> getBackupFilePaths(BackupJob job, int progressPercentageWhenApiRetern,
            int progressPercentageWhenSumComputed) {
        Objects.requireNonNull(backup);
        var list = new ArrayList<BackupContext>();
        var files = backup.getFiles();

        // save progress
        job.setProgress(Integer.valueOf(progressPercentageWhenApiRetern));

        // calcuate filesize sum

        long fileSizeSum = 0;
        for (Path path : files) {
            var fileSize = FileUtil.getFileSize(path);
            log.debug("file size:" + fileSize);
            fileSizeSum += fileSize;
            list.add(new BackupContext(path, session));
        }

        // save progress
        int  rate = progressPercentageWhenSumComputed;

        log.debug("file size sum:" + fileSizeSum);
        long denominator = BigDecimal.valueOf(fileSizeSum).divide(BigDecimal.valueOf(100-rate).divide(BigDecimal.valueOf(100)), 2, RoundingMode.UP).longValue();
        long numerator = BigDecimal.valueOf(denominator).multiply(BigDecimal.valueOf(rate)).divide(BigDecimal.valueOf(100), new MathContext(0, RoundingMode.DOWN)).longValue();
        log.debug("num/dnm={}/{}", numerator, denominator);

        job.setProgressDenominator(denominator);
        job.addProgressNumerator(numerator);

        return list;
    }

    /**
     * release resources.
     */
    @Override
    public void close() {
        log.debug("close backup jobId:" + jobId);

        if (backup != null) {
            try {
                backup.close();
            } catch (IOException | InterruptedException | ServerException ex) {
                // ignore
                log.debug("close backup", ex);
            }
        }

        if (datastoreClient != null) {
            try {
                datastoreClient.close();
            } catch (IOException | InterruptedException | ServerException ex) {
                // ignore
                log.debug("close datastoreClient", ex);

            }
        }

        if (session != null) {
            try {
                session.close();
            } catch (IOException | InterruptedException | ServerException ex) {
                // ignore
                log.debug("close session", ex);
            }
        }

    }

}
