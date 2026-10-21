package com.tsurugidb.belayer.webapi.exec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * ScriptDebug sets the debug flag of the external commands only while the
 * given logger is at DEBUG.
 */
public class ScriptDebugTest {

    Logger logger;
    Level savedLevel;

    @BeforeEach
    public void setUp() {
        logger = (Logger) LoggerFactory.getLogger(DbShutdownExec.class);
        savedLevel = logger.getLevel();
    }

    @AfterEach
    public void tearDown() {
        logger.setLevel(savedLevel);
    }

    @Test
    public void test_set_when_debug() {
        logger.setLevel(Level.DEBUG);
        var pb = new ProcessBuilder("true");

        ScriptDebug.apply(pb, logger);

        assertEquals("true", pb.environment().get(ScriptDebug.ENV));
    }

    @Test
    public void test_not_set_otherwise() {
        logger.setLevel(Level.INFO);
        var pb = new ProcessBuilder("true");
        pb.environment().remove(ScriptDebug.ENV);

        ScriptDebug.apply(pb, logger);

        assertFalse(pb.environment().containsKey(ScriptDebug.ENV));
    }
}
