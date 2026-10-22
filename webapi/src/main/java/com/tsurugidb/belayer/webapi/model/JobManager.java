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

import java.io.File;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.Period;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tsurugidb.belayer.webapi.dto.Job;
import com.tsurugidb.belayer.webapi.dto.Job.JobStatus;
import com.tsurugidb.belayer.webapi.exception.BadRequestException;
import com.tsurugidb.belayer.webapi.exception.IORuntimeException;
import com.tsurugidb.belayer.webapi.exception.NotFoundException;

import lombok.extern.slf4j.Slf4j;

/**
 * Manager of jobs.
 *
 * <p>
 * Jobs are kept in memory and saved to a JSON file. Jobs older than the configured expiration days are removed.
 * </p>
 * <p>
 * Reading jobs does not write the file. The file is written in the background: right after a job is
 * registered, updated or canceled through this class, and periodically, so that changes made directly to a
 * job (status of a transaction, progress, result files) are also saved. The file is written only when its
 * content changes, and it is replaced atomically.
 * </p>
 * <p>
 * The file is never written while holding the lock of this manager, because writing it reads every job and
 * some getters of a job take the lock of the job.
 * </p>
 */
@Slf4j
@Component
public class JobManager implements DisposableBean {

  private static final String DUMP_FILE_NAME = "belayer_jobs.json";

  private static final String TEMP_FILE_SUFFIX = ".tmp";

  /** Interval to save the changes made directly to jobs. */
  static final long SAVE_INTERVAL_SECONDS = 5;

  @Value("${webapi.job.data.directory}")
  private String dumpFileDir;

  @Value("${webapi.job.data.expiration.days}")
  private int expirationDays;

  private String jsonFilePath;

  @Autowired
  SystemTime systemTime;

  @Autowired
  private ObjectMapper mapper;

  /** Jobs. Guarded by this. null after {@link #destroy()}. */
  private LinkedHashMap<String, Job> jobMap = new LinkedHashMap<>();

  /** Lock to write the file. */
  private final Object fileLock = new Object();

  /** Content last written to the file. Guarded by fileLock. */
  private byte[] lastSaved;

  private final AtomicBoolean saveRequested = new AtomicBoolean(false);

  private ScheduledExecutorService saver;

  /**
   * Initialize the job list from the JSON file, and start saving the job list in the background.
   *
   * @throws IOException not thrown by the current implementation (a JSON file that cannot be read is deleted instead).
   */
  @PostConstruct
  public void init() throws IOException {
    jsonFilePath = dumpFileDir + "/" + DUMP_FILE_NAME;

    readJobListFromJson();

    saver = Executors.newSingleThreadScheduledExecutor(runnable -> {
      Thread thread = new Thread(runnable, "belayer-job-saver");
      thread.setDaemon(true);
      return thread;
    });
    saver.scheduleWithFixedDelay(this::saveQuietly, SAVE_INTERVAL_SECONDS, SAVE_INTERVAL_SECONDS, TimeUnit.SECONDS);
  }

  /**
   * Register job.
   *
   * @param job Job to start.
   * @return Job
   * @throws IllegalArgumentException if this manager has been destroyed.
   */
  public Job registerJob(Job job) {

    synchronized (this) {
      if (jobMap == null) {
        throw new IllegalArgumentException("Not acceptable job. Now in shutdown mode.");
      }
      log.debug("register job:" + job.getJobId());

      job.setStartTime(systemTime.now());

      jobMap.put(getId(job.getUid(), job.getJobId()), job);
    }
    requestSave();
    return job;
  }

  /**
   * Update Job status
   *
   * Does nothing after this manager has been destroyed.
   *
   * @param job       Job
   * @param jobStatus jobStatus to change
   * @param th        thoowable when exception occurred
   * @throws IllegalArgumentException if the job is not found.
   */
  public void updateJobStatus(Job job, JobStatus jobStatus, Throwable th) {
    if (isDestroyed()) {
      log.debug("ignore the status of job {} after shutdown", job.getJobId());
      return;
    }
    var targetJob = getJob(job.getType(), job.getUid(), job.getJobId());
    if (targetJob.isEmpty()) {
      throw new IllegalArgumentException("Target job is not found.");
    }

    var target = targetJob.get();
    synchronized (target) {
      // skip update when job has already canceled.
      if (target.getStatus() != JobStatus.CANCELED) {
        target.setStatus(jobStatus);
        target.setProgress(Job.PROGRESS_COMPLETED);

        if (th != null) {
          target.setErrorMessage(th.getMessage());
        }
        target.setEndTime(systemTime.now());
        log.debug("close session:{}", job.getJobId());
        target.close();
        log.debug("session closed:{}", job.getJobId());
      }
    }
    requestSave();
  }

  /**
   * Cancel job.
   *
   * @param type  backup or restore
   * @param uid   Uer ID
   * @param jobId Job ID
   * @return Job
   * @throws NotFoundException if the job is not found.
   * @throws BadRequestException if the job can not be canceled.
   */
  public Job cancelJob(String type, String uid, String jobId) {
    var jobOp = getJob(type, uid, jobId);
    if (jobOp.isEmpty()) {
      throw new NotFoundException("Specified job is not found. jobId:" + jobId,
          "job is not found. jobId:" + jobId, null);
    }

    var job = jobOp.get();
    log.debug("job:" + job);

    synchronized (job) {
      var status = job.getStatus();
      if (!job.canCancel()) {
        throw new BadRequestException(String.format("Can't cancel job. status=%s", status),
            String.format("Can't cancel job. status=%s", status), null);
      }

      // cancel job if alived.
      log.debug("cancel job:" + job);
      job.setStatus(JobStatus.CANCELED);
      job.setProgress(Job.PROGRESS_COMPLETED);
      job.setEndTime(systemTime.now());
      job.cancelJob();
      job.close();
    }
    requestSave();
    return job;
  }

  /**
   * Return specified Job.
   *
   * Expired jobs are removed from memory, but the file is not written.
   *
   * @param type  job type
   * @param uid   User ID
   * @param jobId Job ID
   * @return Job (empty after this manager has been destroyed)
   */
  public Optional<Job> getJob(String type, String uid, String jobId) {
    Job job;
    synchronized (this) {
      if (jobMap == null) {
        return Optional.empty();
      }
      cleanUpOldJobs();
      job = jobMap.get(getId(uid, jobId));
    }

    if (job == null || !job.getType().equals(type)) {
      return Optional.empty();
    }

    return Optional.of(job);
  }

  /**
   * Return all Jobs.
   *
   * Expired jobs are removed from memory, but the file is not written.
   *
   * @return list of jobs, newest first (empty after this manager has been destroyed).
   */
  public List<Job> getAllJobs() {
    List<Job> values;
    synchronized (this) {
      if (jobMap == null) {
        return new LinkedList<>();
      }
      cleanUpOldJobs();
      values = new LinkedList<>(jobMap.values());
    }
    Collections.reverse(values);
    return values;
  }

  private String getId(String uid, String jobId) {
    return uid + ":" + jobId;
  }

  private synchronized boolean isDestroyed() {
    return jobMap == null;
  }

  /**
   * Callcel all STARTED jobs before destroy component, and save the job list.
   *
   * After this, the manager does not accept new jobs, and the other methods do nothing.
   */
  @Override
  public void destroy() throws Exception {

    List<Job> runningJobs;
    synchronized (this) {
      if (jobMap == null) {
        return;
      }
      runningJobs = jobMap.values().stream()
          .filter(job -> job.getStatus() == JobStatus.RUNNING)
          .collect(Collectors.toList());
    }

    // cancel jobs without holding the lock of this manager, since cancelJob() takes the lock of each job.
    for (Job job : runningJobs) {
      try {
        this.cancelJob(job.getType(), job.getUid(), job.getJobId());
      } catch (RuntimeException ex) {
        log.warn("failed to cancel job. jobId:" + job.getJobId(), ex);
      }
    }

    if (saver != null) {
      // the periodic save is canceled. a save already requested is finished.
      saver.shutdown();
      saver.awaitTermination(SAVE_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    saveQuietly();

    synchronized (this) {
      jobMap = null;
    }
  }

  /**
   * Read the job list from the JSON file, if it exists.
   *
   * If the file cannot be read, it is deleted and the job list is cleared.
   */
  public void readJobListFromJson() {
    synchronized (this) {
      if (Files.exists(Path.of(jsonFilePath)) && !Files.isDirectory(Path.of(jsonFilePath))) {
        try {
          jobMap = mapper.readValue(new File(jsonFilePath), new TypeReference<LinkedHashMap<String, Job>>() {
          });
        } catch (IOException ex) {
          try {
            Files.delete(Path.of(jsonFilePath));
          } catch (IOException ignore) {
            // ignore
          }
          jobMap = new LinkedHashMap<>();
        }
      }
    }
  }

  /**
   * Request to save the job list in the background.
   *
   * Requests made before the save starts are saved together.
   */
  public void requestSave() {
    if (saver == null || !saveRequested.compareAndSet(false, true)) {
      return;
    }
    try {
      saver.execute(() -> {
        saveRequested.set(false);
        saveQuietly();
      });
    } catch (RejectedExecutionException ex) {
      // shutting down. destroy() saves the job list.
      saveRequested.set(false);
    }
  }

  /**
   * Wait until the saves requested so far have finished. (for tests)
   *
   * @throws Exception if interrupted or the wait failed.
   */
  void awaitBackgroundSave() throws Exception {
    saver.submit(() -> {
    }).get(SAVE_INTERVAL_SECONDS * 2, TimeUnit.SECONDS);
  }

  private void saveQuietly() {
    try {
      dumpJobListToJson();
    } catch (RuntimeException ex) {
      log.warn("failed to save the job list. file:" + jsonFilePath, ex);
    }
  }

  /**
   * Remove expired jobs, then write the job list to the JSON file if it has changed.
   *
   * <p>
   * The file is replaced atomically: the list is written to a temporary file in the same directory, which is then
   * renamed to the JSON file. Does nothing after this manager has been destroyed.
   * </p>
   * <p>
   * Do not call this while holding the lock of a job: writing the file reads every job, and some getters of a job
   * take its lock.
   * </p>
   *
   * @throws IORuntimeException if an I/O error occurred while writing the file.
   */
  public void dumpJobListToJson() {
    synchronized (fileLock) {
      Map<String, Job> snapshot;
      synchronized (this) {
        if (jobMap == null) {
          return;
        }
        cleanUpOldJobs();
        snapshot = new LinkedHashMap<>(jobMap);
      }

      try {
        // serialize without holding the lock of this manager.
        byte[] json = mapper.writeValueAsBytes(snapshot);
        if (Arrays.equals(json, lastSaved)) {
          return;
        }
        writeAtomically(Path.of(jsonFilePath), json);
        lastSaved = json;
      } catch (IOException ex) {
        throw new IORuntimeException("failed to save the job list. file:" + jsonFilePath, ex);
      }
    }
  }

  private void writeAtomically(Path target, byte[] content) throws IOException {
    Path parent = target.toAbsolutePath().getParent();
    Files.createDirectories(parent);
    Path temp = parent.resolve(target.getFileName().toString() + TEMP_FILE_SUFFIX);
    try {
      Files.write(temp, content);
      try {
        Files.move(temp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
      } catch (AtomicMoveNotSupportedException ex) {
        log.debug("atomic move is not supported. file:" + target);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  /**
   * Remove jobs started before the configured expiration days from memory.
   */
  public void cleanUpOldJobs() {
    synchronized (this) {
      if (jobMap == null || jobMap.isEmpty()) {
        return;
      }
      Instant expTime = systemTime.now().minus(Period.ofDays(expirationDays));
      List<String> expired = new ArrayList<>();
      for (Map.Entry<String, Job> entry : jobMap.entrySet()) {
        if (isExpired(entry.getValue(), expTime)) {
          expired.add(entry.getKey());
        }
      }
      expired.forEach(jobMap::remove);
    }
  }

  private boolean isExpired(Job job, Instant expTime) {
    boolean expired = job.getStartTime().isBefore(expTime);
    log.debug("expire:{}, jobId:{}, expdays:{}, start:{}, exp:{}",
        expired, job.getJobId(), expirationDays, job.getStartTime(), expTime);
    return expired;
  }

}
