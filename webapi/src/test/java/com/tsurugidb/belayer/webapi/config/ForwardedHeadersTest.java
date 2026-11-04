package com.tsurugidb.belayer.webapi.config;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.BodyInserters;

/**
 * With BELAYER_FORWARD_HEADERS_STRATEGY=framework, the server takes the URL the
 * client requested from the X-Forwarded-* headers of a reverse proxy (BW-49).
 * endpoints.csv does not exist, so the Endpoints API returns this server's own
 * URL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "BELAYER_FORWARD_HEADERS_STRATEGY=framework",
        "webapi.config.root=build/no-such-config-dir" })
public class ForwardedHeadersTest {

    static final String PUBLIC_ORIGIN = "https://belayer.example.com";

    @LocalServerPort
    int port;

    WebTestClient client;

    @BeforeEach
    public void setUp() {
        client = WebTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    /** Adds the headers a reverse proxy at PUBLIC_ORIGIN would add. */
    static <S extends WebTestClient.RequestHeadersSpec<?>> S forwarded(S spec) {
        spec.header("X-Forwarded-Proto", "https").header("X-Forwarded-Host", "belayer.example.com");
        return spec;
    }

    /** Logs in as a user of the authmock profile and returns the access token. */
    static String login(WebTestClient client) {
        @SuppressWarnings("unchecked")
        Map<String, Object> body = client.post().uri("/api/auth")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData("uid", "tsurugi").with("pw", "password"))
                .exchange()
                .expectStatus().isOk()
                .expectBody(Map.class).returnResult().getResponseBody();
        return (String) body.get("accessToken");
    }

    @Test
    public void test_endpoints_api_returns_the_url_the_client_requested() {
        String token = login(client);

        forwarded(client.get().uri("/api/instance/list")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.endpoints.length()").isEqualTo(1)
                .jsonPath("$.endpoints[0]").isEqualTo(PUBLIC_ORIGIN);
    }

    @Test
    public void test_request_from_the_public_origin_is_not_cross_origin() {
        // A browser sends Origin with a same-origin POST. Unless the forwarded
        // headers are taken into account, the origin differs from the Host the
        // proxy sends and the request is rejected as cross-origin (403).
        forwarded(client.post().uri("/api/auth")
                .header(HttpHeaders.ORIGIN, PUBLIC_ORIGIN)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .body(BodyInserters.fromFormData("uid", "tsurugi").with("pw", "password"))
                .exchange()
                .expectStatus().isOk();
    }
}
