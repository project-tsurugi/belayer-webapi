package com.tsurugidb.belayer.webapi.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tsurugidb.belayer.webapi.config.Router;
import com.tsurugidb.belayer.webapi.dto.DumpJob;
import com.tsurugidb.belayer.webapi.dto.Job;
import com.tsurugidb.belayer.webapi.dto.Job.JobStatus;

/**
 * JobManager saves the job list without being asked to on every read (BW-11).
 */
public class JobManagerTest {

    private static final String UID = "test_user";

    @TempDir
    Path tempDir;

    SystemTime systemTime;
    ObjectMapper mapper;
    Instant now = Instant.parse("2026-10-22T00:00:00.000Z");
    List<JobManager> managers = new ArrayList<>();

    @BeforeEach
    public void setUp() {
        systemTime = mock(SystemTime.class);
        when(systemTime.now()).thenAnswer(inv -> now);
        mapper = new Router().defaultMapper();
    }

    @AfterEach
    public void tearDown() throws Exception {
        for (var manager : managers) {
            manager.destroy();
        }
    }

    private JobManager newManager() throws Exception {
        var manager = new JobManager();
        ReflectionTestUtils.setField(manager, "dumpFileDir", tempDir.toString());
        ReflectionTestUtils.setField(manager, "expirationDays", 3);
        ReflectionTestUtils.setField(manager, "systemTime", systemTime);
        ReflectionTestUtils.setField(manager, "mapper", mapper);
        manager.init();
        managers.add(manager);
        return manager;
    }

    private Path jsonFile() {
        return tempDir.resolve("belayer_jobs.json");
    }

    private DumpJob newJob(String jobId) {
        var job = new DumpJob();
        job.setJobId(jobId);
        job.setUid(UID);
        job.setType(Job.TYPE_DUMP);
        job.setStatus(JobStatus.RUNNING);
        return job;
    }

    private static void waitUntil(BooleanSupplier condition, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (!condition.getAsBoolean()) {
            if (System.currentTimeMillis() > deadline) {
                throw new AssertionError("condition was not met in " + timeoutMillis + "ms");
            }
            Thread.sleep(50);
        }
    }

    private String readJson() {
        try {
            return Files.readString(jsonFile());
        } catch (Exception ex) {
            return "";
        }
    }

    @Test
    public void test_read_does_not_write() throws Exception {
        var manager = newManager();
        manager.registerJob(newJob("job1"));
        manager.awaitBackgroundSave();
        manager.dumpJobListToJson();
        assertTrue(Files.exists(jsonFile()));

        Files.delete(jsonFile());
        assertTrue(manager.getJob(Job.TYPE_DUMP, UID, "job1").isPresent());
        assertEquals(1, manager.getAllJobs().size());
        // nothing has changed, so the periodic save does not write either
        manager.dumpJobListToJson();

        assertFalse(Files.exists(jsonFile()));
    }

    @Test
    public void test_register_update_cancel_saved_in_background() throws Exception {
        var manager = newManager();
        var job = newJob("job1");
        manager.registerJob(job);
        waitUntil(() -> readJson().contains("\"job1\"") && readJson().contains("RUNNING"), 3000);

        manager.updateJobStatus(job, JobStatus.COMPLETED, null);
        waitUntil(() -> readJson().contains("COMPLETED"), 3000);

        var job2 = newJob("job2");
        manager.registerJob(job2);
        manager.cancelJob(Job.TYPE_DUMP, UID, "job2");
        waitUntil(() -> readJson().contains("CANCELED"), 3000);
    }

    @Test
    public void test_direct_change_saved() throws Exception {
        var manager = newManager();
        var job = newJob("job1");
        manager.registerJob(job);
        manager.dumpJobListToJson();

        // changed without going through JobManager, as DumpLoadService and the Execs do
        job.setFiles(List.of("dir/file1.parquet"));
        job.setProgress(42);
        manager.requestSave();
        waitUntil(() -> readJson().contains("dir/file1.parquet") && readJson().contains("42"), 3000);
    }

    @Test
    public void test_direct_change_saved_periodically() throws Exception {
        var manager = newManager();
        var job = newJob("job1");
        manager.registerJob(job);
        manager.awaitBackgroundSave();
        manager.dumpJobListToJson();

        // no request to save: the periodic save picks it up
        job.setProgress(57);
        waitUntil(() -> readJson().contains("57"), (JobManager.SAVE_INTERVAL_SECONDS * 2 + 2) * 1000);
    }

    @Test
    public void test_saved_file_is_complete_and_readable() throws Exception {
        var manager = newManager();
        var job = newJob("job1");
        job.setFiles(List.of("a.parquet"));
        manager.registerJob(job);
        manager.registerJob(newJob("job2"));
        manager.updateJobStatus(job, JobStatus.COMPLETED, null);
        manager.dumpJobListToJson();

        // the temporary file is renamed, not left behind
        try (var files = Files.list(tempDir)) {
            assertEquals(List.of(jsonFile()), files.collect(java.util.stream.Collectors.toList()));
        }

        // the format is unchanged: another manager reads it back
        var other = newManager();
        var read = other.getJob(Job.TYPE_DUMP, UID, "job1");
        assertTrue(read.isPresent());
        assertEquals(JobStatus.COMPLETED, read.get().getStatus());
        assertEquals(List.of("a.parquet"), ((DumpJob) read.get()).getFiles());
        assertEquals(now, read.get().getStartTime());
        assertTrue(other.getJob(Job.TYPE_DUMP, UID, "job2").isPresent());
    }

    @Test
    public void test_expired_jobs_removed_without_saving() throws Exception {
        var manager = newManager();
        manager.registerJob(newJob("job1"));
        manager.awaitBackgroundSave();
        manager.dumpJobListToJson();
        String before = readJson();

        now = now.plus(4, ChronoUnit.DAYS);

        assertTrue(manager.getJob(Job.TYPE_DUMP, UID, "job1").isEmpty());
        assertTrue(manager.getAllJobs().isEmpty());
        assertEquals(before, readJson());

        // removed from the file at the next save
        manager.dumpJobListToJson();
        assertFalse(readJson().contains("job1"));
    }

    @Test
    public void test_after_destroy() throws Exception {
        var manager = newManager();
        var job = newJob("job1");
        manager.registerJob(job);
        manager.destroy();

        // the running job is canceled and saved on destroy
        assertTrue(readJson().contains("CANCELED"));

        assertTrue(manager.getJob(Job.TYPE_DUMP, UID, "job1").isEmpty());
        assertTrue(manager.getAllJobs().isEmpty());
        manager.updateJobStatus(job, JobStatus.COMPLETED, null);
        manager.dumpJobListToJson();
        manager.requestSave();
        assertThrows(IllegalArgumentException.class, () -> manager.registerJob(newJob("job2")));
        assertTrue(readJson().contains("CANCELED"));
    }

    @Test
    public void test_concurrent_register_and_read() throws Exception {
        var manager = newManager();
        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int t = 0; t < 2; t++) {
                final int base = t * 1000;
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < 100; i++) {
                        manager.registerJob(newJob("job" + (base + i)));
                    }
                }));
                futures.add(pool.submit(() -> {
                    for (int i = 0; i < 100; i++) {
                        manager.getAllJobs();
                        manager.getJob(Job.TYPE_DUMP, UID, "job" + i);
                    }
                }));
            }
            for (var future : futures) {
                future.get();
            }
        } finally {
            pool.shutdownNow();
        }
        assertEquals(200, manager.getAllJobs().size());
    }

    @Test
    public void test_no_deadlock_with_job_lock() throws Exception {
        var manager = newManager();
        var job = newJob("job1");
        manager.registerJob(job);

        assertTimeoutPreemptively(Duration.ofSeconds(20), () -> {
            CompletableFuture<Void> save;
            synchronized (job) {
                // the save reads the job, which needs the lock of the job held here
                save = CompletableFuture.runAsync(manager::dumpJobListToJson);
                Thread.sleep(300);
                // while the save waits for the job, the manager can still be used
                manager.getJob(Job.TYPE_DUMP, UID, "job1");
                manager.updateJobStatus(job, JobStatus.COMPLETED, null);
            }
            save.get();
        });
        manager.dumpJobListToJson();
        assertTrue(readJson().contains("COMPLETED"));
    }
}
