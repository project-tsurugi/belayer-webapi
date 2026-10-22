package com.tsurugidb.belayer.webapi.config;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.test.web.reactive.server.WebTestClient;

/**
 * The OpenAPI document does not show the items of the request bodies that
 * the server sets by itself as inputs (BW-3).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        ManagementPortEnvironmentPostProcessor.ENABLED_PROPERTY + "=true",
        "management.server.port=0" })
public class OpenApiDocumentTest {

    @LocalManagementPort
    int managementPort;

    @Test
    public void test_server_side_items_not_shown_as_input() {
        // The first request for the OpenAPI document builds it, which takes
        // longer than WebTestClient's default of 5 seconds.
        var client = WebTestClient.bindToServer().baseUrl("http://localhost:" + managementPort)
                .responseTimeout(Duration.ofSeconds(60)).build();

        client.get().uri("/management/openapi/springdocDefault").exchange()
                .expectStatus().isOk()
                .expectBody()
                // load: set from the authentication and the path
                .jsonPath("$.components.schemas.LoadRequestParam.properties.files").exists()
                .jsonPath("$.components.schemas.LoadRequestParam.properties.format").exists()
                .jsonPath("$.components.schemas.LoadRequestParam.properties.uid").doesNotExist()
                .jsonPath("$.components.schemas.LoadRequestParam.properties.credentials").doesNotExist()
                .jsonPath("$.components.schemas.LoadRequestParam.properties.table").doesNotExist()
                .jsonPath("$.components.schemas.LoadRequestParam.properties.jobId").doesNotExist()
                // delete: uid is in the response, but not an input
                .jsonPath("$.components.schemas.DeleteTarget.properties.uid.readOnly").isEqualTo(true)
                .jsonPath("$.components.schemas.DeleteTarget.properties.path").exists()
                // downloadzip: request only
                .jsonPath("$.components.schemas.DownloadZip.properties.pathList").exists()
                .jsonPath("$.components.schemas.DownloadZip.properties.uid").doesNotExist();
    }
}
