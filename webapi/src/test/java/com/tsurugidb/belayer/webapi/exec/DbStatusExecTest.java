package com.tsurugidb.belayer.webapi.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tsurugidb.belayer.webapi.exception.ProcessExecException;

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

        var status = exec.runProcess(tempDir.resolve("mon.log").toString(), "token");

        assertEquals("true", status.getStatus());
    }

    @Test
    public void test_script_debug_off_otherwise() {
        logger.setLevel(Level.INFO);

        var status = exec.runProcess(tempDir.resolve("mon.log").toString(), "token");

        assertEquals("unset", status.getStatus());
    }

    // BW-9: the monitoring file is removed after each call.

    private void useScript(String body) throws Exception {
        Path script = tempDir.resolve("status2.sh");
        Files.writeString(script, "#!/bin/bash\n" + body);
        script.toFile().setExecutable(true);
        exec.cmdString = script + " %s %s %s %s %s";
    }

    @Test
    public void test_monitoring_file_removed() throws Exception {
        // the monitoring file is the third argument. report its path as the status.
        useScript("touch \"$3\"\necho \"{\\\"status\\\": \\\"$3\\\"}\"\n");

        var status = exec.getStatus("job", "token");

        Path monitoringFile = Path.of(status.getStatus());
        assertTrue(monitoringFile.getFileName().toString().startsWith("monitoring-"));
        assertFalse(Files.exists(monitoringFile));
    }

    @Test
    public void test_monitoring_file_removed_on_failure() throws Exception {
        Path reported = tempDir.resolve("reported.txt");
        useScript("touch \"$3\"\necho -n \"$3\" > " + reported + "\necho 'not json'\n");

        assertThrows(ProcessExecException.class, () -> exec.getStatus("job", "token"));

        Path monitoringFile = Path.of(Files.readString(reported));
        assertFalse(Files.exists(monitoringFile));
    }

    @Test
    public void test_monitoring_file_not_made() throws Exception {
        // the command does not create the monitoring file: nothing to delete, no error
        useScript("echo '{\"status\": \"running\"}'\n");

        assertEquals("running", exec.getStatus("job", "token").getStatus());
    }
}
