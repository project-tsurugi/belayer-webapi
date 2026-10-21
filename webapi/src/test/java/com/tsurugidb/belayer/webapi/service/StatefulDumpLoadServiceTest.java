package com.tsurugidb.belayer.webapi.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.tsurugidb.belayer.webapi.dto.Job;
import com.tsurugidb.belayer.webapi.dto.Job.JobStatus;
import com.tsurugidb.belayer.webapi.dto.LongTransactionJob;
import com.tsurugidb.belayer.webapi.dto.TransactionApiParameter;
import com.tsurugidb.belayer.webapi.dto.TransactionFinishType;
import com.tsurugidb.belayer.webapi.dto.TsurugiTransaction;
import com.tsurugidb.belayer.webapi.exception.BadRequestException;
import com.tsurugidb.belayer.webapi.exception.NotFoundException;
import com.tsurugidb.belayer.webapi.model.JobManager;
import com.tsurugidb.tsubakuro.sql.Transaction;
import com.tsurugidb.tsubakuro.util.FutureResponse;

public class StatefulDumpLoadServiceTest {

    private static final String UID = "user";
    private static final String TRANSACTION_ID = "tx1";

    StatefulDumpLoadService service;
    JobManager jobManager;
    Transaction transaction;

    @BeforeEach
    @SuppressWarnings("unchecked")
    public void setUp() throws Exception {
        jobManager = mock(JobManager.class);
        transaction = mock(Transaction.class);
        when(transaction.rollback()).thenReturn(mock(FutureResponse.class));

        service = new StatefulDumpLoadService();
        service.jobManager = jobManager;
    }

    private LongTransactionJob registerJob(JobStatus status) {
        var job = new LongTransactionJob();
        job.setUid(UID);
        job.setJobId(TRANSACTION_ID);
        job.setType(Job.TYPE_TRANSACTION);
        job.setStatus(status);
        job.setTsurugiTransaction(TsurugiTransaction.builder().jobId(TRANSACTION_ID).transaction(transaction).build());
        when(jobManager.getJob(Job.TYPE_TRANSACTION, UID, TRANSACTION_ID)).thenReturn(Optional.of(job));
        return job;
    }

    private TransactionApiParameter rollbackParam() {
        var param = new TransactionApiParameter();
        param.setUid(UID);
        param.setTransactionId(TRANSACTION_ID);
        param.setFinishType(TransactionFinishType.ROLLBACK);
        return param;
    }

    @Test
    public void test_rollback_available() throws Exception {
        var job = registerJob(JobStatus.AVAILABLE);

        var result = service.finishTransaction(rollbackParam()).block();

        assertSame(job, result);
        verify(transaction).rollback();
        verify(jobManager).updateJobStatus(eq(job), eq(JobStatus.ROLLBACK_COMPLETED), isNull());
    }

    @Test
    public void test_rollback_in_use() throws Exception {
        var job = registerJob(JobStatus.IN_USE);

        var result = service.finishTransaction(rollbackParam()).block();

        assertSame(job, result);
        verify(transaction).rollback();
        verify(jobManager).updateJobStatus(eq(job), eq(JobStatus.ROLLBACK_COMPLETED), isNull());
    }

    @Test
    public void test_rollback_already_committed() throws Exception {
        registerJob(JobStatus.COMMITED);

        assertThrows(BadRequestException.class, () -> service.finishTransaction(rollbackParam()));

        verify(transaction, never()).rollback();
        verify(jobManager, never()).updateJobStatus(any(), any(), any());
    }

    private TransactionApiParameter statusParam() {
        var param = new TransactionApiParameter();
        param.setUid(UID);
        param.setTransactionId(TRANSACTION_ID);
        return param;
    }

    @Test
    public void test_get_transaction() throws Exception {
        var job = registerJob(JobStatus.AVAILABLE);

        var result = service.getTransaction(statusParam()).block();

        assertSame(job, result);
        verify(transaction, never()).rollback();
        verify(jobManager, never()).updateJobStatus(any(), any(), any());
    }

    @Test
    public void test_get_transaction_not_found() throws Exception {
        when(jobManager.getJob(Job.TYPE_TRANSACTION, UID, TRANSACTION_ID)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getTransaction(statusParam()));
    }
}
