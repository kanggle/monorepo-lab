package com.example.security.oauth2.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link IamClientCredentialsTokenProvider} (TASK-MONO-501, ADR-MONO-058 § D6).
 * MockWebServer stands in for the authorization server's token endpoint — Docker-free.
 */
class IamClientCredentialsTokenProviderTest {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);

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

    private String tokenUri() {
        return server.url("/oauth2/token").toString();
    }

    private static MockResponse tokenResponse(String accessToken, long expiresIn) {
        return new MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"access_token\":\"" + accessToken + "\",\"expires_in\":" + expiresIn
                        + ",\"token_type\":\"Bearer\"}");
    }

    // ---- token acquisition happy path + caching ----

    @Test
    @DisplayName("fetches a client_credentials token via POST and returns access_token")
    void fetchesToken() throws InterruptedException {
        server.enqueue(tokenResponse("abc123", 1800));

        IamClientCredentialsTokenProvider provider = new IamClientCredentialsTokenProvider(
                tokenUri(), "test-client", "test-secret", "internal.invoke",
                DEFAULT_TIMEOUT, DEFAULT_TIMEOUT);

        assertThat(provider.currentBearer()).isEqualTo("abc123");

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/oauth2/token");
        assertThat(req.getBody().readUtf8()).isEqualTo("grant_type=client_credentials&scope=internal.invoke");
    }

    @Test
    @DisplayName("a valid cached token is reused — the endpoint is hit only once for repeated calls")
    void cachesToken() throws InterruptedException {
        server.enqueue(tokenResponse("cached-tok", 1800));

        IamClientCredentialsTokenProvider provider = new IamClientCredentialsTokenProvider(
                tokenUri(), "test-client", "test-secret", "internal.invoke",
                DEFAULT_TIMEOUT, DEFAULT_TIMEOUT);

        assertThat(provider.currentBearer()).isEqualTo("cached-tok");
        assertThat(provider.currentBearer()).isEqualTo("cached-tok");
        assertThat(provider.currentBearer()).isEqualTo("cached-tok");

        assertThat(server.getRequestCount()).isEqualTo(1);
        server.takeRequest();
    }

    @Test
    @DisplayName("a near-expiry token (expires_in inside the refresh skew) is re-fetched, not reused")
    void refetchesNearExpiryToken() {
        server.enqueue(tokenResponse("t1", 10)); // 10s < 60s REFRESH_SKEW -> never considered valid
        server.enqueue(tokenResponse("t2", 10));

        IamClientCredentialsTokenProvider provider = new IamClientCredentialsTokenProvider(
                tokenUri(), "test-client", "test-secret", "internal.invoke",
                DEFAULT_TIMEOUT, DEFAULT_TIMEOUT);

        assertThat(provider.currentBearer()).isEqualTo("t1");
        assertThat(provider.currentBearer()).isEqualTo("t2");
        assertThat(server.getRequestCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("no access_token in the response is a hard failure, not a silent fallback")
    void missingAccessTokenFailsHard() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody("{\"expires_in\":1800,\"token_type\":\"Bearer\"}"));

        IamClientCredentialsTokenProvider provider = new IamClientCredentialsTokenProvider(
                tokenUri(), "test-client", "test-secret", "internal.invoke",
                DEFAULT_TIMEOUT, DEFAULT_TIMEOUT);

        assertThatThrownBy(provider::currentBearer).isInstanceOf(IllegalStateException.class);
    }

    // ---- scope is a parameter, not hardcoded (fan-platform/community-service shape) ----

    @Test
    @DisplayName("scope is parameterized per instance, not a hardcoded literal")
    void scopeIsParameterizedPerInstance() throws InterruptedException {
        server.enqueue(tokenResponse("tok-a", 1800));
        server.enqueue(tokenResponse("tok-b", 1800));

        new IamClientCredentialsTokenProvider(
                tokenUri(), "client-a", "secret-a", "membership.read",
                DEFAULT_TIMEOUT, DEFAULT_TIMEOUT).currentBearer();
        new IamClientCredentialsTokenProvider(
                tokenUri(), "client-b", "secret-b", "internal.invoke",
                DEFAULT_TIMEOUT, DEFAULT_TIMEOUT).currentBearer();

        RecordedRequest first = server.takeRequest();
        RecordedRequest second = server.takeRequest();
        assertThat(first.getBody().readUtf8()).isEqualTo("grant_type=client_credentials&scope=membership.read");
        assertThat(second.getBody().readUtf8()).isEqualTo("grant_type=client_credentials&scope=internal.invoke");
    }

    @Test
    @DisplayName("a null or blank scope omits the scope parameter entirely, rather than sending an empty one")
    void blankScopeOmitsScopeParameter() throws InterruptedException {
        server.enqueue(tokenResponse("tok", 1800));
        server.enqueue(tokenResponse("tok2", 1800));

        new IamClientCredentialsTokenProvider(
                tokenUri(), "client", "secret", null,
                DEFAULT_TIMEOUT, DEFAULT_TIMEOUT).currentBearer();
        new IamClientCredentialsTokenProvider(
                tokenUri(), "client", "secret", "   ",
                DEFAULT_TIMEOUT, DEFAULT_TIMEOUT).currentBearer();

        RecordedRequest nullScopeReq = server.takeRequest();
        RecordedRequest blankScopeReq = server.takeRequest();
        assertThat(nullScopeReq.getBody().readUtf8()).isEqualTo("grant_type=client_credentials");
        assertThat(blankScopeReq.getBody().readUtf8()).isEqualTo("grant_type=client_credentials");
    }

    // ---- RFC 7617 UTF-8 Basic-auth encoding — the exact defect ADR-MONO-058 § D6 closes ----

    @Test
    @DisplayName("RFC 7617: Basic-auth credential bytes are UTF-8-encoded, not the JVM platform-default charset")
    void basicAuthHeaderIsUtf8EncodedNotPlatformDefault() throws InterruptedException {
        // Non-ASCII client id/secret: UTF-8 and ISO-8859-1 (a plausible platform-default charset
        // on a non-UTF-8 host) encode these code points to DIFFERENT byte sequences, so the
        // Base64 header this class produces diverges depending on which charset it actually uses.
        // Credentials made only of ASCII bytes would pass even with a platform-default-charset
        // bug on any JVM whose default happens to already be UTF-8 (most CI runners) — that is
        // precisely why the credentials below are deliberately non-ASCII, per the task's own
        // required test shape.
        String clientId = "cliente-éureka"; // e-acute
        String clientSecret = "sécrèt-카팍"; // e-acute, e-grave, Korean syllables

        server.enqueue(tokenResponse("tok", 1800));

        IamClientCredentialsTokenProvider provider = new IamClientCredentialsTokenProvider(
                tokenUri(), clientId, clientSecret, "internal.invoke",
                DEFAULT_TIMEOUT, DEFAULT_TIMEOUT);
        provider.currentBearer();

        RecordedRequest req = server.takeRequest();
        String actualHeader = req.getHeader("Authorization");

        String expectedUtf8Header = "Basic " + Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
        String wrongIso88591Header = "Basic " + Base64.getEncoder()
                .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.ISO_8859_1));

        assertThat(actualHeader).isEqualTo(expectedUtf8Header);
        assertThat(actualHeader).isNotEqualTo(wrongIso88591Header);
    }

    // ---- timeouts are required constructor parameters, no default that means "no timeout" ----

    @Test
    @DisplayName("null connectTimeout/readTimeout is rejected, not silently defaulted to \"no timeout\"")
    void nullTimeoutsAreRejected() {
        assertThatThrownBy(() -> new IamClientCredentialsTokenProvider(
                tokenUri(), "client", "secret", "scope", null, DEFAULT_TIMEOUT))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new IamClientCredentialsTokenProvider(
                tokenUri(), "client", "secret", "scope", DEFAULT_TIMEOUT, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("zero/negative timeouts are rejected — Duration.ZERO means \"no timeout\" to SimpleClientHttpRequestFactory")
    void zeroOrNegativeTimeoutsAreRejected() {
        assertThatThrownBy(() -> new IamClientCredentialsTokenProvider(
                tokenUri(), "client", "secret", "scope", Duration.ZERO, DEFAULT_TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connectTimeout");
        assertThatThrownBy(() -> new IamClientCredentialsTokenProvider(
                tokenUri(), "client", "secret", "scope", DEFAULT_TIMEOUT, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("readTimeout");
        assertThatThrownBy(() -> new IamClientCredentialsTokenProvider(
                tokenUri(), "client", "secret", "scope", Duration.ofSeconds(-1), DEFAULT_TIMEOUT))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @Timeout(10)
    @DisplayName("a configured readTimeout is honored: a hung token endpoint fails fast instead of blocking indefinitely")
    void readTimeoutIsHonored() {
        // No body is ever written back and the connection is never closed, so the request hangs
        // on the socket read until either the client's own read timeout fires, or (if the
        // timeout were not actually wired in) the JUnit @Timeout above fails the test instead.
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        IamClientCredentialsTokenProvider provider = new IamClientCredentialsTokenProvider(
                tokenUri(), "client", "secret", "scope",
                Duration.ofSeconds(5), Duration.ofMillis(300));

        assertThatThrownBy(provider::currentBearer).isInstanceOf(RestClientException.class);
    }
}
