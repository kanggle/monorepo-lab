package com.example.fanplatform.community.infrastructure.membership;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link IamClientCredentialsTokenProvider} (TASK-FAN-BE-010 AC-3).
 * MockWebServer stands in for the IAM token endpoint — Docker-free.
 */
class IamClientCredentialsTokenProviderTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private IamClientCredentialsTokenProvider provider() {
        return new IamClientCredentialsTokenProvider(
                server.url("/oauth2/token").toString(),
                "community-service-client",
                "secret");
    }

    @Test
    @DisplayName("Basic auth + grant_type=client_credentials sent; access_token returned")
    void fetchesToken() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"access_token\":\"abc123\",\"expires_in\":1800,\"token_type\":\"Bearer\"}"));

        String token = provider().currentBearer();

        assertThat(token).isEqualTo("abc123");
        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        String expectedBasic = "Basic " + Base64.getEncoder()
                .encodeToString("community-service-client:secret".getBytes());
        assertThat(req.getHeader("Authorization")).isEqualTo(expectedBasic);
        assertThat(req.getBody().readUtf8()).isEqualTo("grant_type=client_credentials");
    }

    @Test
    @DisplayName("token is cached — second call does not re-fetch")
    void cachesToken() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"access_token\":\"cached-tok\",\"expires_in\":1800,\"token_type\":\"Bearer\"}"));

        IamClientCredentialsTokenProvider p = provider();
        assertThat(p.currentBearer()).isEqualTo("cached-tok");
        assertThat(p.currentBearer()).isEqualTo("cached-tok");

        // Only ONE token request hit the server (the second call used the cache).
        assertThat(server.getRequestCount()).isEqualTo(1);
        server.takeRequest();
    }

    @Test
    @DisplayName("near-expiry token (expires_in < REFRESH_SKEW) is re-fetched each call")
    void refreshesNearExpiryToken() {
        // expires_in=10s is inside the 60s refresh skew → never cached as valid.
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"access_token\":\"t1\",\"expires_in\":10,\"token_type\":\"Bearer\"}"));
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"access_token\":\"t2\",\"expires_in\":10,\"token_type\":\"Bearer\"}"));

        IamClientCredentialsTokenProvider p = provider();
        assertThat(p.currentBearer()).isEqualTo("t1");
        assertThat(p.currentBearer()).isEqualTo("t2");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }
}
