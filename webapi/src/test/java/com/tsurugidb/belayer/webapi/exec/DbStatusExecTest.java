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
package com.tsurugidb.belayer.webapi.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.tsurugidb.belayer.webapi.dto.ExecStatus;
import com.tsurugidb.belayer.webapi.model.FileWatcher;
import com.tsurugidb.belayer.webapi.model.MonitoringManager;

@SpringBootTest
public class DbStatusExecTest {

  @MockBean
  MonitoringManager monitoringManager;

  @Autowired
  DbStatusExec target;

  @Test
  public void testIsOnline_true() throws Exception {
  
    target.cmdString = "echo a tateyama-server is running";
    target.conf = "./test/java/conf_dir";

    doAnswer(new Answer<Void>(){
      @Override
      public Void answer(InvocationOnMock invoke) {
          FileWatcher param = invoke.getArgument(0,FileWatcher.class);
          var status = new ExecStatus();
          status.setStatus(ExecStatus.STATUS_RUNNNING);
          param.setExecStatus(status);
          param.setMonitoringManager(monitoringManager);
          return null;
      }
    }).when(monitoringManager).addFileWatcher(any());

    assertEquals(true, target.isOnline("jobId"), "isOnline");

  }

  @Test
  public void testIsOnline_false() throws Exception {
    target.cmdString = "echo no db is running";
    target.conf = "./test/java/conf_dir";
    doAnswer(new Answer<Void>(){
      @Override
      public Void answer(InvocationOnMock invoke) {
          FileWatcher param = invoke.getArgument(0,FileWatcher.class);
          var status = new ExecStatus();
          status.setStatus(ExecStatus.STATUS_STOP);
          param.setExecStatus(status);
          param.setMonitoringManager(monitoringManager);
          return null;
      }
    }).when(monitoringManager).addFileWatcher(any());


    assertEquals(false, target.isOnline("jobId"), "isOnline");
  }

}
