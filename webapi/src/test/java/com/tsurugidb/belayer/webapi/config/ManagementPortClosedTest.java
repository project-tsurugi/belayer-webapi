package com.tsurugidb.belayer.webapi.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/**
 * With belayer.management.enabled=false the management port is not opened.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        ManagementPortEnvironmentPostProcessor.ENABLED_PROPERTY + "=false" })
public class ManagementPortClosedTest {

    @Autowired
    Environment environment;

    @Test
    public void test_management_port_closed_when_disabled() {
        assertEquals("-1", environment.getProperty("management.server.port"));
        assertEquals("false", environment.getProperty("springdoc.api-docs.enabled"));
        assertEquals("false", environment.getProperty("springdoc.swagger-ui.enabled"));
        // Spring Boot sets this only when it starts a management server.
        assertNull(environment.getProperty("local.management.port"));
    }
}
