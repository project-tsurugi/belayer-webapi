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

import java.io.Closeable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.As;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.Disposable;

/**
 * Base class of the asynchronous jobs managed by Belayer (backup, restore, dump, load and transaction).
 *
 * <p>Serialized to JSON with the job type in the {@code type} property, which selects the subclass
 * on deserialization. Progress is either set directly as a percentage or computed from a
 * numerator/denominator pair (see {@link #getProgress()}).</p>
 */
@JsonTypeInfo(use = Id.NAME, include = As.PROPERTY, property = "type")
@JsonSubTypes({
    @Type(value = BackupJob.class, name = Job.TYPE_BACKUP),
    @Type(value = RestoreJob.class, name = Job.TYPE_RESTORE),
    @Type(value = DumpJob.class, name = Job.TYPE_DUMP),
    @Type(value = LoadJob.class, name = Job.TYPE_LOAD),
    @Type(value = LongTransactionJob.class, name = Job.TYPE_TRANSACTION),
})
@Data
@Slf4j
public abstract class Job implements Closeable {

  /** Progress value of a completed job (100%). */
  public static final Integer PROGRESS_COMPLETED = Integer.valueOf(100);

  /** Job type of {@link BackupJob}. */
  public static final String TYPE_BACKUP = "backup";
  /** Job type of {@link RestoreJob}. */
  public static final String TYPE_RESTORE = "restore";
  /** Job type of {@link DumpJob}. */
  public static final String TYPE_DUMP = "dump";
  /** Job type of {@link LoadJob}. */
  public static final String TYPE_LOAD = "load";
  /** Job type of {@link LongTransactionJob}. */
  public static final String TYPE_TRANSACTION = "transaction";

  private String jobId;

  private String uid;

  @JsonIgnore
  @NotNull
  private Object credentials;

  private String type;

  private JobStatus status;

  private Instant startTime;

  private Instant endTime;

  @JsonIgnore
  private Disposable disposable;

  private String errorMessage;

  @JsonIgnore
  private long progressNumerator;

  @JsonIgnore
  private long progressDenominator;

  /** progress percentage (0-100) */
  private Integer progress;

  @Override
  public void close() {
    log.debug("Job is closed. class:{} job:{}", getClass(), jobId);
    disposable = null;
  }

  /**
   * Cancels the job by disposing its running reactive subscription, if any.
   */
  public void cancelJob() {
    if (disposable != null) {
      disposable.dispose();
    }
  }

  /**
   * Returns whether this job can be canceled.
   *
   * @return {@code true} if the job status is {@link JobStatus#RUNNING}
   */
  public boolean canCancel() {

    return status == JobStatus.RUNNING;
  }

  /**
   * Sets the progress percentage directly. It takes precedence over the numerator/denominator pair
   * until {@link #addProgressNumerator(long)} is called.
   *
   * @param actualProgress progress percentage (0-100), or {@code null} to use the numerator/denominator pair
   */
  public synchronized void setProgress(Integer actualProgress) {
    progress = actualProgress;
    log.debug(getType() + " progress:" + this.getProgress());
  }

  /**
   * Returns the progress percentage.
   *
   * @return the value set by {@link #setProgress(Integer)} if any; otherwise numerator * 100 / denominator
   *         (rounded half up), or 0 if the denominator is 0
   */
  public synchronized Integer getProgress() {
    if (this.progress != null) {
      return this.progress;
    }

    if (progressDenominator == 0) {
      return 0;
    }

    var v = BigDecimal.valueOf(progressNumerator * 100);
    return v.divide(BigDecimal.valueOf(progressDenominator), 2, RoundingMode.HALF_UP).intValue();
  }

  /**
   * Adds to the progress numerator and clears the progress set by {@link #setProgress(Integer)},
   * so that the progress is computed from the numerator/denominator pair from now on.
   *
   * @param additionalProgressValue amount to add to the numerator
   */
  public synchronized void addProgressNumerator(long additionalProgressValue) {
    this.progress = null;
    this.progressNumerator += additionalProgressValue;
    log.debug(getType() + " progress:" + this.getProgress());
  }

  /**
   * Sets the progress denominator, i.e. the total amount of work.
   *
   * @param progressDenominator total amount of work
   */
  public synchronized void setProgressDenominator(long progressDenominator) {
    this.progressDenominator = progressDenominator;
  }

  /**
   * Status of a job.
   */
  public static enum JobStatus {
    /** The job is running. */
    RUNNING,
    /** The job was canceled. */
    CANCELED,
    /** The job failed. */
    FAILED,
    /** The job completed successfully. */
    COMPLETED,

    /** Transaction: started and ready for use. */
    AVAILABLE,
    /** Transaction: in use by a dump or load. */
    IN_USE,
    /** Transaction: committed and finished. */
    COMMITED,
    /** Transaction: rolled back and finished. */
    ROLLBACK_COMPLETED;

    @Override
    public String toString() {
      return this.name();
    }
  }

}
