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

  public static final String TYPE_BACKUP = "backup";
  public static final String TYPE_RESTORE = "restore";
  public static final String TYPE_DUMP = "dump";
  public static final String TYPE_LOAD = "load";
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

  @Override
  public void close() {
    log.debug("Job is closed. class:{} job:{}", getClass(), jobId);
    disposable = null;
  }

  public void cancelJob() {
    if (disposable != null) {
      disposable.dispose();
    }
  }

  public boolean canCancel() {

    return status == JobStatus.RUNNING;
  }

  public static enum JobStatus {
    RUNNING,
    CANCELED,
    FAILED,
    COMPLETED,

    AVAILABLE,
    IN_USE,
    COMMITED,
    ROLLBACK_COMPLETED;

    @Override
    public String toString() {
      return this.name();
    }
  }

}
