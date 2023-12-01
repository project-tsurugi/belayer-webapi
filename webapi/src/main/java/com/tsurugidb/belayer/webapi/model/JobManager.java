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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Period;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import com.tsurugidb.belayer.webapi.exception.NotFoundException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class JobManager implements DisposableBean {

  private static final String DUMP_FILE_NAME = "belayer_jobs.json";

  @Value("${webapi.job.data.directory}")
  private String dumpFileDir;

  @Value("${webapi.job.data.expiration.days}")
  private int expirationDays;

  private String jsonFilePath;

  @Autowired
  SystemTime systemTime;

  @Autowired
  private ObjectMapper mapper;

  private LinkedHashMap<String, Job> jobMap = new LinkedHashMap<>();

  @PostConstruct
  public void init() throws IOException {
    jsonFilePath = dumpFileDir + "/" + DUMP_FILE_NAME;

    readJobListFromJson();
  }

  /**
   * Register job.
   *
   * @param job Job to start.
   * @return Job
   */
  public Job registerJob(Job job) {

    synchronized (this) {
      if (jobMap == null) {
        throw new IllegalArgumentException("Not acceptable job. Now in shutdown mode.");
      }
      log.debug("register job:" + job.getJobId());

      job.setStartTime(systemTime.now());

      jobMap.put(getId(job.getUid(), job.getJobId()), job);

      dumpJobListToJson();
      return job;
    }
  }

  /**
   * Update Job status
   * 
   * @param job       Job
   * @param jobStatus jobStatus to change
   * @param th        thoowable when exception occurred
   */
  public void updateJobStatus(Job job, JobStatus jobStatus, Throwable th) {
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
    dumpJobListToJson();
  }

  /**
   * Cancel job.
   *
   * @param type  backup or restore
   * @param uid   Uer ID
   * @param jobId Job ID
   * @return Job
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

      dumpJobListToJson();
      return job;
    }
  }

  /**
   * Return specified Job.
   * 
   * @param uid   User ID
   * @param jobId Job ID
   * @return Job
   */
  public Optional<Job> getJob(String type, String uid, String jobId) {
    dumpJobListToJson();
    Job job = jobMap.get(getId(uid, jobId));

    if (job == null || !job.getType().equals(type)) {
      return Optional.empty();
    }

    return Optional.of(job);
  }

  /**
   * Return all Jobs.
   *
   * @return list of jobs.
   */
  public List<Job> getAllJobs() {
    dumpJobListToJson();

    List<Job> values = new LinkedList<>(jobMap.values());
    Collections.reverse(values);
    return values;
  }

  private String getId(String uid, String jobId) {
    return uid + ":" + jobId;
  }

  /**
   * Callcel all STARTED jobs before destroy component.
   */
  @Override
  public void destroy() throws Exception {

    synchronized (this) {
      jobMap.values().stream()
          .filter(job -> job.getStatus() == JobStatus.RUNNING)
          .forEach(job -> this.cancelJob(job.getType(), job.getUid(), job.getJobId()));

      dumpJobListToJson();
      jobMap = null;
    }
  }

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

  public void dumpJobListToJson() {
    synchronized (this) {
      cleanUpOldJobs();

      try {
        var parent = Path.of(jsonFilePath).getParent();
        if (!Files.exists(parent)) {
          Files.createDirectories(parent);
        }
        mapper.writeValue(new File(jsonFilePath), jobMap);
      } catch (IOException ex) {
        throw new RuntimeException(ex);
      }
    }
  }

  public void cleanUpOldJobs() {
    synchronized (this) {
      LinkedHashMap<String, Job> newJobMap = jobMap.entrySet().stream()
          .filter(entry -> !isExpired(entry.getValue()))
          .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b,
              () -> new LinkedHashMap<String, Job>()));
      jobMap = newJobMap;
    }

  }

  private boolean isExpired(Job job) {
    var expTime = systemTime.now().minus(Period.ofDays(expirationDays));
    boolean expired = job.getStartTime().isBefore(expTime);
    log.debug("expire:{}, jobId:{}, expdays:{}, start:{}, exp:{}",
        expired, job.getJobId(), expirationDays, job.getStartTime(), expTime);
    return expired;
  }

}
