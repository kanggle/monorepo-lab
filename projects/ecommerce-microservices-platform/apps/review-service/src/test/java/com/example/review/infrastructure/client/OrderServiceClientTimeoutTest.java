package com.example.review.infrastructure.client;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Timeout-specific unit test for {@link OrderServiceClient} (ADR-MONO-058 D7 / TASK-BE-570).
 *
 * <p>Before this task, {@code OrderServiceClient} was built from a bare
 * {@code RestClient.builder().baseUrl(baseUrl).build()} with NO request factory — zero
 * connect/read timeout. This call sits synchronously in the review-creation request path
 * (purchase verification), so a hung order-service would block a user-facing request
 * indefinitely — the exact "live production-risk gap" the ADR calls out. This test proves the
 * fix: a hung order-service response now fails FAST within the configured read timeout, using
 * a real {@link MockWebServer} (not a mocked transport) so the timeout is actually exercised at
 * the socket level.
 */
class OrderServiceClientTimeoutTest {

    private MockWebServer orderService;

    @BeforeEach
    void setUp() throws IOException {
        orderService = new MockWebServer();
        orderService.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        orderService.shutdown();
    }

    private String baseUrl() {
        return "http://" + orderService.getHostName() + ":" + orderService.getPort();
    }

    @Test
    @DisplayName("read timeout honored: order-service hangs -> call fails within the configured bound, not forever")
    void hungOrderService_failsFastWithinConfiguredReadTimeout() {
        // No response enqueued: MockWebServer accepts the connection but never writes a
        // response body, simulating a hung downstream call.
        OrderServiceClient client = new OrderServiceClient(baseUrl(), 2000, 300);

        long start = System.nanoTime();
        assertThatThrownBy(() -> client.hasUserPurchasedProduct(UUID.randomUUID(), UUID.randomUUID()))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Purchase verification failed");
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        // Generous upper bound (10x the configured 300ms read timeout) to absorb CI scheduling
        // jitter while still proving the call did NOT hang indefinitely.
        assertThat(elapsedMs).isLessThan(3000);
    }

    @Test
    @DisplayName("happy path unaffected by the ResilienceClientFactory migration")
    void respondingOrderService_stillWorks() {
        orderService.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"purchased\":true}"));

        OrderServiceClient client = new OrderServiceClient(baseUrl(), 2000, 5000);

        boolean result = client.hasUserPurchasedProduct(UUID.randomUUID(), UUID.randomUUID());

        assertThat(result).isTrue();
    }
}
