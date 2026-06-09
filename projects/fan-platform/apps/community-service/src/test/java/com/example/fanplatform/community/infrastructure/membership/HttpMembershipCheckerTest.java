package com.example.fanplatform.community.infrastructure.membership;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link HttpMembershipChecker} (TASK-FAN-BE-010 AC-2). Uses
 * MockWebServer as the membership-service stand-in — Docker-free, runs in
 * {@code :community-service:check}.
 *
 * <p>Asserts the fail-closed contract: {@code allowed} verbatim on 2xx; {@code
 * false} on any error (non-2xx, malformed body, connection error); and that a
 * Bearer token is attached on every request.
 */
class HttpMembershipCheckerTest {

    private MockWebServer server;
    private HttpMembershipChecker checker;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        RestClient restClient = RestClient.builder()
                .baseUrl(server.url("/").toString())
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth("test-bearer");
                    return execution.execute(request, body);
                })
                .build();
        checker = new HttpMembershipChecker(restClient);
    }

    @AfterEach
    void tearDown() {
        try {
            server.shutdown();
        } catch (Exception ignore) {
            // already shut down by a test (e.g. connectionError) — tolerate.
        }
    }

    @Test
    @DisplayName("200 {allowed:true} → true, and Bearer + query params sent")
    void allowedTrue() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"allowed\":true}"));

        boolean result = checker.hasAccess("fan-1", "PREMIUM", "fan-platform");

        assertThat(result).isTrue();
        RecordedRequest req = server.takeRequest();
        assertThat(req.getPath()).isEqualTo(
                "/internal/membership/access?accountId=fan-1&tier=PREMIUM&tenantId=fan-platform");
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer test-bearer");
    }

    @Test
    @DisplayName("200 {allowed:false} → false (domain deny is NOT an error)")
    void allowedFalse() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"allowed\":false}"));

        assertThat(checker.hasAccess("fan-1", "MEMBERS_ONLY", "fan-platform")).isFalse();
    }

    @Test
    @DisplayName("500 → false (fail-closed)")
    void serverError() {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThat(checker.hasAccess("fan-1", "PREMIUM", "fan-platform")).isFalse();
    }

    @Test
    @DisplayName("401 (auth rejected) → false (fail-closed)")
    void unauthorized() {
        server.enqueue(new MockResponse().setResponseCode(401));

        assertThat(checker.hasAccess("fan-1", "PREMIUM", "fan-platform")).isFalse();
    }

    @Test
    @DisplayName("malformed / absent allowed field → false (fail-closed)")
    void malformedBody() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("not-json"));

        assertThat(checker.hasAccess("fan-1", "PREMIUM", "fan-platform")).isFalse();
    }

    @Test
    @DisplayName("connection error (server down) → false (fail-closed)")
    void connectionError() throws IOException {
        server.shutdown(); // nothing listening → connection refused

        assertThat(checker.hasAccess("fan-1", "PREMIUM", "fan-platform")).isFalse();
    }
}
