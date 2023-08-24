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
package com.tsurugidb.belayer.webapi.service;

import java.io.IOException;
import java.util.Objects;
import java.util.Optional;

import org.eclipse.jetty.io.RuntimeIOException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.tsurugidb.belayer.webapi.dto.Job;
import com.tsurugidb.belayer.webapi.dto.Job.JobStatus;
import com.tsurugidb.belayer.webapi.dto.LongTransactionJob;
import com.tsurugidb.belayer.webapi.dto.TransactionApiParameter;
import com.tsurugidb.belayer.webapi.dto.TransactionFinishType;
import com.tsurugidb.belayer.webapi.dto.TransactionMode;
import com.tsurugidb.belayer.webapi.exception.BadRequestException;
import com.tsurugidb.belayer.webapi.exception.NotFoundException;
import com.tsurugidb.belayer.webapi.model.JobManager;
import com.tsurugidb.sql.proto.SqlRequest.TransactionType;
import com.tsurugidb.tsubakuro.channel.common.connection.RememberMeCredential;
import com.tsurugidb.tsubakuro.exception.ServerException;

import reactor.core.publisher.Mono;

@Component
public class StatefulDumpLoadService {

    @Autowired
    JobManager jobManager;

    @Autowired
    TsubakuroService tsubakuroService;

    public Mono<LongTransactionJob> startTransaction(TransactionApiParameter param) {

        var job = new LongTransactionJob();
        job.setUid(param.getUid());
        job.setCredentials(param.getCredentials());
        job.setJobId(param.getTransactionId());
        job.setType(Job.TYPE_TRANSACTION);
        job.setStatus(JobStatus.AVAILABLE);

        var tranMode = param.getTransactionMode();
        TransactionType type = null;
        if (tranMode == TransactionMode.READ_ONLY) {
            type = TransactionType.READ_ONLY;
        } else if (tranMode == TransactionMode.READ_WRITE) {
            type = TransactionType.LONG;
        }
        job.setTransactionMode(tranMode);

        if (type != null) {
            var credentials = param.getCredentials();
            Objects.requireNonNull(credentials);
            var cred = new RememberMeCredential(credentials);
            var tran = tsubakuroService.createTransaction(type, cred, param.getUid(), Optional.of(param.getTimeoutMin()), true, param.getTables());

            job.setTsurugiTransaction(tran);
        }

        jobManager.registerJob(job);

        return Mono.just(job);
    }

    public Mono<LongTransactionJob> finishTransaction(TransactionApiParameter param) {
        var targetJob = jobManager.getJob(Job.TYPE_TRANSACTION, param.getUid(), param.getTransactionId());
        if (targetJob.isEmpty()) {
            var message = "Specified transaction is not found. transactionId:" + param.getTransactionId();
            throw new NotFoundException(message, message, null);
        }
        var job = (LongTransactionJob) targetJob.get();

        var finishType = param.getFinishType();
        if (finishType == TransactionFinishType.COMMIT) {
            synchronized (job) {
                if (job.getStatus() != JobStatus.AVAILABLE) {
                    var msg = String.format("Specified transction in not for use. [transactionId:%s, status:%s]",
                            job.getJobId(), job.getStatus());
                    throw new BadRequestException(msg, msg);
                }
                commitTransaction(job);
            }
        } else if (finishType == TransactionFinishType.ROLLBACK) {
            synchronized (job) {
                if (job.getStatus() != JobStatus.AVAILABLE || job.getStatus() != JobStatus.IN_USE) {
                    var msg = String.format("Specified transction in not for use. [transactionId:%s, status:%s]",
                            job.getJobId(), job.getStatus());
                    throw new BadRequestException(msg, msg);
                }
                rollbackTransaction(job);
            }
        } else if (finishType == TransactionFinishType.SHOW_STATUS) {
            // do nothing.
        } else {
            throw new IllegalArgumentException("Invalid finishType. finishType=" + finishType);
        }

        return Mono.just(job);
    }

    public void commitTransaction(LongTransactionJob job) {

        try {
            job.getTsurugiTransaction().getTransaction().commit().await();
        } catch (IOException | ServerException | InterruptedException ex) {
            throw new RuntimeIOException("failed to commit transation", ex);
        }

        jobManager.updateJobStatus(job, JobStatus.COMMITED, null);
    }

    public void rollbackTransaction(LongTransactionJob job) {

        try {
            job.getTsurugiTransaction().getTransaction().rollback().await();
        } catch (IOException | ServerException | InterruptedException ex) {
            throw new RuntimeIOException("failed to rollback transation", ex);
        }

        jobManager.updateJobStatus(job, JobStatus.ROLLBACK_COMPLETED, null);
    }
}
