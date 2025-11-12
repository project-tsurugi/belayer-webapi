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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.tsurugidb.belayer.webapi.dto.DbStatus;
import com.tsurugidb.belayer.webapi.dto.ExecStatus;
import com.tsurugidb.belayer.webapi.dto.InstanceInfo;
import com.tsurugidb.belayer.webapi.exec.DbShutdownExec;
import com.tsurugidb.belayer.webapi.exec.DbStartExec;
import com.tsurugidb.belayer.webapi.exec.DbStatusExec;

@Component
public class DbControlService {

  @Autowired
  DbStartExec dbStartExec;

  @Autowired
  DbShutdownExec dbShutdownExec;

  @Autowired
  DbStatusExec dbStatusExec;

  @Autowired
  InstanceInfoService instanceInfoService;

  /**
   * start Tsurugi DB.
   * @param jobId Job ID
   * @param token authentication token
   * @param mode launch mode
   */
  public void startDatabase(String jobId, String token, String mode) {
    dbStartExec.startDatabse(jobId, token, mode);
  }

  /**
   * shutdown Tsurugi DB.
   * @param jobId Job ID
   * @param token authentication token
   */
  public void shutdownDatabase(String jobId, String token) {
    dbShutdownExec.shutdownDatabase(jobId, token);
  }

  /**
   * get staus of Tsurugi DB.
   * @param jobId Job ID
   * @return DB status
   */
  public DbStatus getStatus(String jobId) {

    DbStatus dbStatus = dbStatusExec.getStatus(jobId);
    InstanceInfo instanceInfo = instanceInfoService.getInstanceInfo();

    dbStatus.setInstanceName(instanceInfo.getInstanceName());
    dbStatus.setTags(instanceInfo.getTags());

    return dbStatus;
  }

  /**
   * determine Tsurugi DB is running
   * @param jobId Job ID
   * @return true if Tsurugi DB is running
   */
  public boolean isOnline(String jobId) {
    
    var status = getStatus(jobId);
    return ExecStatus.STATUS_RUNNING.equals(status.getStatus());
  }

}
