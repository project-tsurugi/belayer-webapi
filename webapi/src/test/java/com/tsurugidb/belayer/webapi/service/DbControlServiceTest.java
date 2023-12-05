package com.tsurugidb.belayer.webapi.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.tsurugidb.belayer.webapi.exec.DbShutdownExec;
import com.tsurugidb.belayer.webapi.exec.DbStartExec;
import com.tsurugidb.belayer.webapi.exec.DbStatusExec;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
public class DbControlServiceTest {

    @Autowired
    DbControlService dbControlService;

    @MockBean
    DbStartExec dbStartExec;

    @MockBean
    DbShutdownExec dbShutdownExec;

    @MockBean
    DbStatusExec dbStatusExec;

    @Test
    public void test_startDatabase() throws Exception {
        doNothing().when(dbStartExec).startDatabse(anyString());
        dbControlService.startDatabase("test");
    }

    @Test
    public void test_shutdownDatabase() throws Exception {
        doNothing().when(dbShutdownExec).shutdownDatabase(anyString());
        dbControlService.shutdownDatabase("test");
    }

    @Test
    public void test_getStatus() throws Exception {
        when(dbStatusExec.getStatus(any())).thenReturn("running");
        var result = dbControlService.getStatus("test");

        assertEquals("running", result);
    }

}
