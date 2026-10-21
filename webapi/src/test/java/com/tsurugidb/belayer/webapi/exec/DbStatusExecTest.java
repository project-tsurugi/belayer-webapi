package com.tsurugidb.belayer.webapi.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * DbStatusExec tells the status script to write its debug log only while it
 * logs at DEBUG itself.
 */
public class DbStatusExecTest {

    @TempDir
    Path tempDir;

    DbStatusExec exec;
    Logger logger;
    Level savedLevel;

    @BeforeEach
    public void setUp() throws Exception {
        // Stands in for show_db_status.sh: reports what it was given in the
        // environment as the status.
        Path script = tempDir.resolve("status.sh");
        Files.writeString(script,
                "#!/bin/bash\necho \"{\\\"status\\\": \\\"${" + ScriptDebug.ENV + ":-unset}\\\"}\"\n");
        script.toFile().setExecutable(true);

        exec = new DbStatusExec();
        exec.cmdString = script + " %s %s %s %s %s";
        exec.tgctl = "tgctl";
        exec.tgha = "tgha";
        exec.conf = "tsurugi.ini";
        ReflectionTestUtils.setField(exec, "mapper", new ObjectMapper());

        logger = (Logger) LoggerFactory.getLogger(DbStatusExec.class);
        savedLevel = logger.getLevel();
    }

    @AfterEach
    public void tearDown() {
        logger.setLevel(savedLevel);
    }

    @Test
    public void test_script_debug_on_when_debug() {
        logger.setLevel(Level.DEBUG);

        var status = exec.runProcess(tempDir.resolve("mon.log").toString(), "", "token");

        assertEquals("true", status.getStatus());
    }

    @Test
    public void test_script_debug_off_otherwise() {
        logger.setLevel(Level.INFO);

        var status = exec.runProcess(tempDir.resolve("mon.log").toString(), "", "token");

        assertEquals("unset", status.getStatus());
    }
}
