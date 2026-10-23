package com.tsurugidb.belayer.webapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.actuate.metrics.AutoConfigureMetrics;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * By default the management port is open and serves health and prometheus
 * only: info, env, beans, the OpenAPI document and Swagger UI are not there.
 * Tests switch the Prometheus registry off unless asked to keep it, hence
 * {@link AutoConfigureMetrics}.
 */
@AutoConfigureMetrics
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "management.server.port=0" })
public class ManagementPortDefaultTest {

    @LocalManagementPort
    int managementPort;

    @Test
    public void test_management_port_serves_health_and_prometheus_only() {
        var client = WebTestClient.bindToServer().baseUrl("http://localhost:" + managementPort).build();

        client.get().uri("/management/health").exchange().expectStatus().isOk();
        client.get().uri("/management/prometheus").exchange().expectStatus().isOk();
        for (String hidden : new String[] { "info", "env", "beans", "openapi/springdocDefault", "swagger-ui" }) {
            client.get().uri("/management/" + hidden).exchange().expectStatus().isNotFound();
        }
    }
}
