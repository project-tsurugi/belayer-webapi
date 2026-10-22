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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.tsurugidb.belayer.webapi.dto.Job;
import com.tsurugidb.belayer.webapi.dto.Job.JobStatus;
import com.tsurugidb.belayer.webapi.dto.LongTransactionJob;
import com.tsurugidb.belayer.webapi.dto.TransactionApiParameter;
import com.tsurugidb.belayer.webapi.dto.TransactionFinishType;
import com.tsurugidb.belayer.webapi.dto.TransactionMode;
import com.tsurugidb.belayer.webapi.exception.BadRequestException;
import com.tsurugidb.belayer.webapi.exception.IORuntimeException;
import com.tsurugidb.belayer.webapi.exception.NotFoundException;
import com.tsurugidb.belayer.webapi.model.JobManager;
import com.tsurugidb.sql.proto.SqlRequest.TransactionType;
import com.tsurugidb.tsubakuro.channel.common.connection.RememberMeCredential;
import com.tsurugidb.tsubakuro.exception.ServerException;

import reactor.core.publisher.Mono;

/**
 * Service for transactions used by the stateful dump/load APIs.
 */
@Component
public class StatefulDumpLoadService {

    @Autowired
    JobManager jobManager;

    @Autowired
    TsubakuroService tsubakuroService;

    /**
     * Start a transaction and register it as a transaction job.
     *
     * A READ_ONLY mode starts a read-only transaction and a READ_WRITE mode starts a long transaction.
     *
     * @param param transaction API parameter.
     * @return registered transaction job.
     */
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
            var tran = tsubakuroService.createTransaction(type, cred, param.getUid(), "stream_api", Optional.of(param.getTimeoutMin()), true, param.getTables());

            job.setTsurugiTransaction(tran);
        }

        jobManager.registerJob(job);

        return Mono.just(job);
    }

    /**
     * Return the transaction to show its status.
     *
     * @param param transaction API parameter. The finish type is not used.
     * @return transaction job.
     * @throws NotFoundException if the transaction is not found.
     */
    public Mono<LongTransactionJob> getTransaction(TransactionApiParameter param) {
        return Mono.just(findTransactionJob(param));
    }

    /**
     * Finish the transaction by commit or rollback.
     *
     * @param param transaction API parameter.
     * @return transaction job.
     * @throws NotFoundException if the transaction is not found.
     * @throws BadRequestException if the status of the transaction does not allow the requested operation.
     * @throws IllegalArgumentException if the finish type is invalid.
     */
    public Mono<LongTransactionJob> finishTransaction(TransactionApiParameter param) {
        var job = findTransactionJob(param);

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
                if (job.getStatus() != JobStatus.AVAILABLE && job.getStatus() != JobStatus.IN_USE) {
                    var msg = String.format("Specified transction in not for use. [transactionId:%s, status:%s]",
                            job.getJobId(), job.getStatus());
                    throw new BadRequestException(msg, msg);
                }
                rollbackTransaction(job);
            }
        } else {
            throw new IllegalArgumentException("Invalid finishType. finishType=" + finishType);
        }

        return Mono.just(job);
    }

    private LongTransactionJob findTransactionJob(TransactionApiParameter param) {
        var targetJob = jobManager.getJob(Job.TYPE_TRANSACTION, param.getUid(), param.getTransactionId());
        if (targetJob.isEmpty()) {
            var message = "Specified transaction is not found. transactionId:" + param.getTransactionId();
            throw new NotFoundException(message, message, null);
        }
        return (LongTransactionJob) targetJob.get();
    }

    /**
     * Commit the transaction and mark the job as committed.
     *
     * @param job transaction job.
     * @throws IORuntimeException if the commit failed.
     */
    public void commitTransaction(LongTransactionJob job) {

        try {
            job.getTsurugiTransaction().getTransaction().commit().await();
        } catch (IOException | ServerException ex) {
            throw new IORuntimeException("failed to commit transation", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IORuntimeException("failed to commit transation", ex);
        }

        jobManager.updateJobStatus(job, JobStatus.COMMITED, null);
    }

    /**
     * Roll back the transaction and mark the job as rollback completed.
     *
     * @param job transaction job.
     * @throws IORuntimeException if the rollback failed.
     */
    public void rollbackTransaction(LongTransactionJob job) {

        try {
            job.getTsurugiTransaction().getTransaction().rollback().await();
        } catch (IOException | ServerException ex) {
            throw new IORuntimeException("failed to rollback transation", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IORuntimeException("failed to rollback transation", ex);
        }

        jobManager.updateJobStatus(job, JobStatus.ROLLBACK_COMPLETED, null);
    }
}
