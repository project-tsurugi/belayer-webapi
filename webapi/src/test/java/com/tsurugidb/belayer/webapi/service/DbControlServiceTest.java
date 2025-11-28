package com.tsurugidb.belayer.webapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.tsurugidb.belayer.webapi.dto.DbStatus;
import com.tsurugidb.belayer.webapi.dto.ExecStatus;
import com.tsurugidb.belayer.webapi.dto.InstanceInfo;
import com.tsurugidb.belayer.webapi.exec.DbShutdownExec;
import com.tsurugidb.belayer.webapi.exec.DbStartExec;
import com.tsurugidb.belayer.webapi.exec.DbStatusExec;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class DbControlServiceTest {

    @Autowired
    DbControlService dbControlService;

    @MockBean
    InstanceInfoService instanceInfoService;

    @MockBean
    DbStartExec dbStartExec;

    @MockBean
    DbShutdownExec dbShutdownExec;

    @MockBean
    DbStatusExec dbStatusExec;

    @Test
    public void test_startDatabase() throws Exception {
        doNothing().when(dbStartExec).startDatabse(anyString(), anyString(), anyString(), anyString(), anyBoolean());
        dbControlService.startDatabase("test", "xxx", "mode", "from", true);
    }

    @Test
    public void test_shutdownDatabase() throws Exception {
        doNothing().when(dbShutdownExec).shutdownDatabase(anyString(), anyString());
        dbControlService.shutdownDatabase("test", "xxx");
    }

    @Test
    public void test_getStatus() throws Exception {
        InstanceInfo iInfo = new InstanceInfo("ins001", List.of("tag1", "tag2"));
        var status = DbStatus.builder().status(ExecStatus.STATUS_RUNNING).instanceName(iInfo.getInstanceName()).tags(iInfo.getTags()).build();
        when(dbStatusExec.getStatus(anyString())).thenReturn(status);
        when(instanceInfoService.getInstanceInfo()).thenReturn(iInfo);

        var result = dbControlService.getStatus("test");

        assertEquals(status, result);
    }

}
