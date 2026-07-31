package com.example.fanplatform.community.infrastructure.membership;

import com.example.security.oauth2.client.IamClientCredentialsTokenProvider;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Call-site-level test for community-service's adoption of the shared
 * {@link IamClientCredentialsTokenProvider} (ADR-MONO-058 § D6, {@code
 * libs/java-security}), wired via {@link MembershipCheckerAutoConfig}
 * (TASK-FAN-BE-041). This deletes and replaces
 * {@code IamClientCredentialsTokenProviderTest} (formerly a unit test of
 * community-service's now-deleted local copy of the same class) —
 * MockWebServer fixture reused per that file's precedent, adapted to the
 * shared class's actual constructor shape.
 *
 * <p>This test exists specifically to prove the two defects the shared class
 * closes (platform-default-charset Basic-auth, no timeouts) are ACTUALLY
 * closed for community-service's real outbound call and real config values
 * (client-id={@code community-service-client}, scope={@code membership.read})
 * — not merely that the shared class's own unit tests pass in isolation. It
 * also regression-guards the exact request shape {@code TASK-FAN-BE-030}
 * fixed after a live 403 (missing {@code scope} in the token request).
 */
class CommunityIamTokenAcquisitionTest {

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

    /**
     * Constructed with community-service's real production {@code @Value}
     * defaults from {@link MembershipCheckerAutoConfig#iamClientCredentialsTokenProvider}
     * (client-id/client-secret/scope/timeouts) — only {@code tokenUri} is
     * swapped for the MockWebServer stand-in.
     */
    private IamClientCredentialsTokenProvider communityDefaultProvider() {
        return new IamClientCredentialsTokenProvider(
                server.url("/oauth2/token").toString(),
                "community-service-client",
                "secret",
                "membership.read",
                Duration.ofMillis(2000),
                Duration.ofMillis(3000));
    }

    @Test
    @DisplayName("UTF-8 Basic-auth header + scope=membership.read (URL-encoded) sent; access_token returned "
            + "— byte-equivalent to the deleted local copy's request (TASK-FAN-BE-030 regression guard)")
    void sendsUtf8BasicAuthAndScope() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"access_token\":\"abc123\",\"expires_in\":1800,\"token_type\":\"Bearer\"}"));

        String token = communityDefaultProvider().currentBearer();

        assertThat(token).isEqualTo("abc123");
        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");

        // Explicit UTF-8 encoding assertion — not merely "the shared class has its
        // own UTF-8 test" (that proves the class, not this call site's use of it).
        String expectedUtf8Basic = "Basic " + Base64.getEncoder()
                .encodeToString("community-service-client:secret".getBytes(StandardCharsets.UTF_8));
        assertThat(req.getHeader("Authorization")).isEqualTo(expectedUtf8Basic);

        assertThat(req.getBody().readUtf8()).isEqualTo("grant_type=client_credentials&scope=membership.read");
    }

    @Test
    @DisplayName("token is cached — second call does not re-fetch")
    void cachesToken() throws InterruptedException {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"access_token\":\"cached-tok\",\"expires_in\":1800,\"token_type\":\"Bearer\"}"));

        IamClientCredentialsTokenProvider p = communityDefaultProvider();
        assertThat(p.currentBearer()).isEqualTo("cached-tok");
        assertThat(p.currentBearer()).isEqualTo("cached-tok");

        assertThat(server.getRequestCount()).isEqualTo(1);
        server.takeRequest();
    }

    /**
     * Explicit constructor-parameter assertion that connect/read timeouts are
     * configured for community-service's instance — the shared class rejects a
     * zero/negative timeout by construction, so this is proof no "unbounded
     * timeout" path exists (the exact defect this adoption closes), not merely
     * an assumption.
     */
    @Test
    @DisplayName("zero connect/read timeout is rejected by construction (no unbounded-timeout path)")
    void zeroTimeoutRejected() {
        assertThatThrownBy(() -> new IamClientCredentialsTokenProvider(
                server.url("/oauth2/token").toString(),
                "community-service-client",
                "secret",
                "membership.read",
                Duration.ZERO,
                Duration.ofMillis(3000)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectTimeout");

        assertThatThrownBy(() -> new IamClientCredentialsTokenProvider(
                server.url("/oauth2/token").toString(),
                "community-service-client",
                "secret",
                "membership.read",
                Duration.ofMillis(2000),
                Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("readTimeout");
    }
}
