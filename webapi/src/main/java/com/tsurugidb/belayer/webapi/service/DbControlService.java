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

  /**
   * start Tsurugi DB.
   * @param jobId Job ID
   */
  public void startDatabase(String jobId) {
    dbStartExec.startDatabse(jobId);
  }

  /**
   * shutdown Tsurugi DB.
   * @param jobId Job ID
   */
  public void shutdownDatabase(String jobId) {
    dbShutdownExec.shutdownDatabase(jobId);
  }

  /**
   * get staus of Tsurugi DB.
   * @param jobId Job ID
   */
  public String getStatus(String jobId) {

    return dbStatusExec.getStatus(jobId);
  }

}
