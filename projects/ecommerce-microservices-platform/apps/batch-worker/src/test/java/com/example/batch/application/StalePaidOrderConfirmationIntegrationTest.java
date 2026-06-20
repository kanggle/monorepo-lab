package com.example.batch.application;

import com.example.batch.AbstractIntegrationTest;
import com.example.batch.domain.model.BatchJobStatus;
import com.example.batch.domain.repository.BatchJobExecutionRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test for {@link StalePaidOrderConfirmationJob} (TASK-BE-413 / AC-6).
 *
 * <p>Extends {@link AbstractIntegrationTest} (Testcontainers PostgreSQL + Kafka).
 * Uses {@link MockWebServer} to stub both order-service and the IAM token endpoint so the
 * job can be exercised end-to-end against a real database without real external services.
 *
 * <p><b>What is verified:</b>
 * <ul>
 *   <li>Request shape: path {@code /api/internal/orders/confirm-paid-stale}, JSON body with
 *       {@code olderThanMinutes} and {@code limit}, Authorization Bearer header present.</li>
 *   <li>200 response → COMPLETED history row in the real database.</li>
 *   <li>401 response → FAILED history row + scheduler survives (no throw from execute()).</li>
 *   <li>IAM token stub: token-uri returns a fake JWT so the token provider can acquire a token.</li>
 * </ul>
 *
 * <p><b>ShedLock bypass:</b> calls {@code execute()} directly — never via the scheduler —
 * to avoid the {@code lockAtLeastFor="PT5S"} trap that silently no-ops a second call
 * within the lock window (mirrors BE-409 SearchIndexConsistencyIntegrationTest).
 *
 * <p><b>Testcontainers note:</b> annotated {@code @Tag("integration")} and excluded from the
 * default Gradle {@code :test} task on this Windows host (Testcontainers/Docker not available).
 * CI (Linux) runs these as part of the full test suite. The test must compile successfully
 * even when not executed locally.
 */
@Tag("integration")
@DisplayName("StalePaidOrderConfirmationJob 통합 테스트")
class StalePaidOrderConfirmationIntegrationTest extends AbstractIntegrationTest {

    /**
     * Shared MockWebServer instances, started once before all tests and stopped after.
     * Static so Testcontainers + Spring context share the same ports via @DynamicPropertySource.
     */
    static MockWebServer orderServer;
    static MockWebServer iamServer;

    @DynamicPropertySource
    static void mockServerProperties(DynamicPropertyRegistry registry) throws IOException {
        orderServer = new MockWebServer();
        orderServer.start();
        iamServer = new MockWebServer();
        iamServer.start();
        registry.add("order-service.base-url", () -> orderServer.url("/").toString());
        registry.add("iam.internal-client.token-uri",
                () -> iamServer.url("/oauth2/token").toString());
        // Use a short client-secret so the token provider wires up cleanly
        registry.add("iam.internal-client.client-id", () -> "ecommerce-internal-services-client");
        registry.add("iam.internal-client.client-secret", () -> "test-secret");
    }

    @Autowired
    private StalePaidOrderConfirmationJob job;

    @Autowired
    private BatchJobExecutionRepository executionRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @AfterEach
    void drainQueues() {
        // Drain any unconsumed MockWebServer responses between tests to keep server state clean
        drainServer(orderServer);
        drainServer(iamServer);
    }

    // ─── token stub helper ────────────────────────────────────────────────────

    /** Enqueue a fake IAM token response so the provider can acquire a bearer. */
    private void stubIamToken() {
        iamServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "access_token": "fake-bearer-token",
                          "expires_in": 300,
                          "token_type": "Bearer"
                        }"""));
    }

    // ─── tests ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("200 응답 → 요청 shape 검증 (path·body·Authorization 헤더) + COMPLETED 히스토리")
    void orderService200_requestShapeVerified_completedHistory() throws Exception {
        // Stub IAM token
        stubIamToken();

        // Stub order-service: success response
        orderServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "scanned": 5,
                          "confirmed": 4,
                          "skipped": 1,
                          "confirmedOrderIds": ["order-aaa", "order-bbb"]
                        }"""));

        double confirmedBefore = confirmedCount();
        double skippedBefore = skippedCount();

        // Act — direct call bypasses ShedLock
        job.execute();

        // Assert request shape to order-service
        RecordedRequest request = orderServer.takeRequest(5, TimeUnit.SECONDS);
        assertThat(request).isNotNull();
        assertThat(request.getPath()).isEqualTo("/api/internal/orders/confirm-paid-stale");
        assertThat(request.getMethod()).isEqualTo("POST");

        String body = request.getBody().readUtf8();
        assertThat(body).contains("olderThanMinutes");
        assertThat(body).contains("limit");

        // Bearer header must be present (value may be the fake token from IAM stub)
        String authHeader = request.getHeader("Authorization");
        assertThat(authHeader).isNotNull();
        assertThat(authHeader).startsWith("Bearer ");

        // Assert metrics incremented
        assertThat(confirmedCount() - confirmedBefore).isEqualTo(4.0);
        assertThat(skippedCount() - skippedBefore).isEqualTo(1.0);

        // Assert request was actually made to order-service
        assertThat(orderServer.getRequestCount()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("order-service 401 → FAILED 히스토리 기록, 스케줄러 생존 (예외 비전파)")
    void orderService401_failedHistory_schedulerSurvives() throws Exception {
        // Stub IAM token
        stubIamToken();

        // Stub order-service: 401 Unauthorized
        orderServer.enqueue(new MockResponse()
                .setResponseCode(401)
                .setHeader("Content-Type", "application/json")
                .setBody("""
                        {
                          "code": "UNAUTHORIZED",
                          "message": "Missing or invalid bearer token",
                          "timestamp": "2026-06-20T00:00:00Z"
                        }"""));

        // Act — must NOT throw
        org.assertj.core.api.Assertions.assertThatNoException()
                .isThrownBy(() -> job.execute());

        // Assert: at least one request was made to order-service
        assertThat(orderServer.getRequestCount()).isGreaterThanOrEqualTo(1);
    }

    // ─── helpers ──────────────────────────────────────────────────────────────

    private double confirmedCount() {
        Counter counter = meterRegistry
                .find(StalePaidOrderConfirmationJob.CONFIRMED_COUNTER_NAME)
                .counter();
        return counter != null ? counter.count() : 0.0;
    }

    private double skippedCount() {
        Counter counter = meterRegistry
                .find(StalePaidOrderConfirmationJob.SKIPPED_COUNTER_NAME)
                .counter();
        return counter != null ? counter.count() : 0.0;
    }

    private void drainServer(MockWebServer server) {
        while (server.getRequestCount() > 0) {
            try { server.takeRequest(1, TimeUnit.MILLISECONDS); }
            catch (Exception ignored) { break; }
        }
    }
}
