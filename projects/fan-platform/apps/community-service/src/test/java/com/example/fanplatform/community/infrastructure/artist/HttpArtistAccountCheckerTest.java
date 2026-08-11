package com.example.fanplatform.community.infrastructure.artist;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okhttp3.mockwebserver.SocketPolicy;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit test for {@link HttpArtistAccountChecker} (TASK-FAN-BE-045 AC-6, ADR-004
 * ACCEPTED — A). Uses MockWebServer as the artist-service stand-in — Docker-free,
 * runs in {@code :community-service:check}. Mirrors
 * {@code HttpMembershipCheckerTest}.
 *
 * <h2>What this pins</h2>
 *
 * The fail-closed contract, <strong>one failure mode per test</strong>. Lumping
 * them into a single "error → false" case would let a regression that reopens
 * exactly one of them (say, a 404 mapped to "absent therefore fine") stay green
 * while the other five still deny. The set of denials is the assertion, so each
 * member is asserted on its own:
 *
 * <ul>
 *   <li>200 {@code {"exists":false}} — a domain "no", NOT an error</li>
 *   <li>200 malformed body</li>
 *   <li>200 with an empty body (no {@code exists} to read at all)</li>
 *   <li>500</li>
 *   <li>404</li>
 *   <li>connection refused (nothing listening)</li>
 *   <li>read timeout (a response that would have said {@code true}, too late)</li>
 * </ul>
 *
 * <p>Only {@code 200 {"exists":true}} yields {@code true}. That case also pins the
 * outgoing request line, because a checker that asks the wrong URL would answer
 * {@code false} for everything and look exactly like a working fail-closed gate.
 */
class HttpArtistAccountCheckerTest {

    private static final String ACCOUNT = "artist-acct-1";
    private static final String TENANT = "fan-platform";

    /**
     * The production default ({@code community.artist-service.read-timeout-ms}).
     *
     * <p>🔴 It is deliberately NOT tightened to make the timeout case fast. A short
     * shared timeout flakes in the other direction: the first request of the class
     * pays JIT + TLS-free-but-still-cold socket setup, exceeds the budget, and the
     * {@code exists:true} case fails fail-closed — a green-looking suite reporting
     * "artist-service says no" when the fixture said yes. (Measured: 400 ms passed
     * in isolation and failed inside the full {@code test} task.) The timeout case
     * gets its own short-timeout client instead.
     */
    private static final Duration READ_TIMEOUT = Duration.ofMillis(3000);

    private MockWebServer server;
    private HttpArtistAccountChecker checker;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        checker = checkerWithReadTimeout(READ_TIMEOUT);
    }

    /**
     * Same request-factory shape {@code ArtistAccountCheckerConfig} builds in
     * production (JDK HttpClient, HTTP/1.1, explicit connect + read timeouts), so
     * the timeout arm below exercises the real mechanism rather than a test-only one.
     */
    private HttpArtistAccountChecker checkerWithReadTimeout(Duration readTimeout) {
        HttpClient httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(readTimeout);
        RestClient restClient = RestClient.builder()
                .baseUrl(server.url("/").toString())
                .requestFactory(factory)
                .requestInterceptor((request, body, execution) -> {
                    request.getHeaders().setBearerAuth("test-bearer");
                    return execution.execute(request, body);
                })
                .build();
        return new HttpArtistAccountChecker(restClient);
    }

    @AfterEach
    void tearDown() {
        try {
            server.shutdown();
        } catch (Exception ignore) {
            // already shut down by a test (e.g. connectionRefused) — tolerate.
        }
    }

    private void enqueueJson(String body) {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody(body));
    }

    @Test
    @DisplayName("200 {exists:true} → true, 그리고 요청 경로·쿼리파라미터가 계약과 정확히 일치")
    void existsTrue_andRequestLineMatchesContract() throws InterruptedException {
        enqueueJson("{\"exists\":true}");

        boolean result = checker.isArtistAccount(ACCOUNT, TENANT);

        assertThat(result).isTrue();
        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("GET");
        assertThat(req.getPath())
                .as("artist-api.md § Internal artist-account existence check")
                .isEqualTo("/internal/artists/exists?accountId=" + ACCOUNT + "&tenantId=" + TENANT);
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer test-bearer");
    }

    @Test
    @DisplayName("200 {exists:false} → false (도메인 거절은 오류가 아니다)")
    void existsFalse() {
        enqueueJson("{\"exists\":false}");

        assertThat(checker.isArtistAccount(ACCOUNT, TENANT)).isFalse();
    }

    @Test
    @DisplayName("200 이지만 본문이 JSON 이 아님 → false (fail-closed)")
    void malformedBody() {
        enqueueJson("not-json");

        assertThat(checker.isArtistAccount(ACCOUNT, TENANT)).isFalse();
    }

    @Test
    @DisplayName("200 이지만 본문이 비어 있음 → false (fail-closed, null 응답 분기)")
    void emptyBody() {
        server.enqueue(new MockResponse()
                .setHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .setBody(""));

        assertThat(checker.isArtistAccount(ACCOUNT, TENANT)).isFalse();
    }

    @Test
    @DisplayName("500 → false (fail-closed)")
    void serverError() {
        server.enqueue(new MockResponse().setResponseCode(500));

        assertThat(checker.isArtistAccount(ACCOUNT, TENANT)).isFalse();
    }

    /**
     * 404 gets its own case because it is the one status a reader is tempted to
     * treat as "the endpoint isn't there, so skip the check". It must deny.
     */
    @Test
    @DisplayName("404 → false (fail-closed — 엔드포인트 부재는 통과 사유가 아니다)")
    void notFound() {
        server.enqueue(new MockResponse().setResponseCode(404));

        assertThat(checker.isArtistAccount(ACCOUNT, TENANT)).isFalse();
    }

    @Test
    @DisplayName("연결 거부 (artist-service 다운) → false (fail-closed)")
    void connectionRefused() throws IOException {
        server.shutdown(); // nothing listening → connection refused

        assertThat(checker.isArtistAccount(ACCOUNT, TENANT)).isFalse();
    }

    /**
     * A hung artist-service is the failure this seam is most likely to meet in
     * production and the one a naive implementation admits on (the request never
     * completes, an unguarded caller blocks, and a "temporarily lenient" retry
     * turns into fail-open). The response is never sent; the adapter must deny on
     * the read timeout.
     */
    @Test
    @DisplayName("읽기 타임아웃 (응답 없음) → false (fail-closed)")
    void readTimeout() {
        // Own client with a short read timeout — the shared one runs at the
        // production default so the non-timeout cases can never trip it (see
        // READ_TIMEOUT). NO_RESPONSE: the server accepts the request and never
        // answers, so the timeout is the only thing that can end this call.
        HttpArtistAccountChecker impatientChecker = checkerWithReadTimeout(Duration.ofMillis(500));
        server.enqueue(new MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE));

        long startedAt = System.nanoTime();
        boolean result = impatientChecker.isArtistAccount(ACCOUNT, TENANT);
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(result).isFalse();
        assertThat(elapsed)
                .as("must deny via the configured read timeout, not hang indefinitely")
                .isLessThan(Duration.ofSeconds(20));
    }
}
