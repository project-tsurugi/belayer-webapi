package com.tsurugidb.belayer.webapi.config;

import static com.tsurugidb.belayer.webapi.config.ForwardedHeadersTest.PUBLIC_ORIGIN;
import static com.tsurugidb.belayer.webapi.config.ForwardedHeadersTest.forwarded;
import static com.tsurugidb.belayer.webapi.config.ForwardedHeadersTest.login;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

/**
 * By default (BELAYER_FORWARD_HEADERS_STRATEGY not set), the X-Forwarded-*
 * headers are ignored and the server goes by the Host of the request (BW-49).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "webapi.config.root=build/no-such-config-dir" })
public class ForwardedHeadersDefaultTest {

    @LocalServerPort
    int port;

    WebTestClient client;

    @BeforeEach
    public void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    public void test_endpoints_api_ignores_forwarded_headers() {
        String token = login(client);

        forwarded(client.get().uri("/api/instance/list")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.endpoints.length()").isEqualTo(1)
                .jsonPath("$.endpoints[0]").isEqualTo("http://localhost:" + port);
    }

    @Test
    public void test_request_from_another_origin_is_rejected() {
        forwarded(client.post().uri("/api/auth")
                .header(HttpHeaders.ORIGIN, PUBLIC_ORIGIN)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .body(BodyInserters.fromFormData("uid", "tsurugi").with("pw", "password"))
                .exchange()
                .expectStatus().isForbidden();
    }
}
