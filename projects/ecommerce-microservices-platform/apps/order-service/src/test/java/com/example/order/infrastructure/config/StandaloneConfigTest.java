package com.example.order.infrastructure.config;

import com.example.order.application.event.OrderPlacedEvent;
import com.example.order.application.port.OrderEventPublisher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit test for {@link StandaloneConfig} (ADR-MONO-058 D7 / TASK-BE-570).
 *
 * <p>Before this task, {@code standaloneOrderEventPublisher} built its {@code RestClient}
 * from a bare {@code RestClient.builder().baseUrl(...).build()} — zero connect/read timeout.
 * Lower urgency than search/review-service (this bean only activates under the
 * {@code standalone} profile), but still a real gap if that profile is used. This test proves
 * the bean is now wired via {@link com.example.common.resilience.ResilienceClientFactory} with
 * an honored, non-zero read timeout, using a real {@link MockWebServer}.
 */
class StandaloneConfigTest {

    private MockWebServer paymentService;
    private final StandaloneConfig config = new StandaloneConfig();

    @BeforeEach
    void setUp() throws IOException {
        paymentService = new MockWebServer();
        paymentService.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        paymentService.shutdown();
    }

    private String baseUrl() {
        return "http://" + paymentService.getHostName() + ":" + paymentService.getPort();
    }

    private OrderPlacedEvent samplePlacedEvent() {
        return OrderPlacedEvent.of(
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                10_000L,
                List.of(new OrderPlacedEvent.Item(UUID.randomUUID().toString(), UUID.randomUUID().toString(), 1, 10_000L)),
                new OrderPlacedEvent.ShippingAddress("recipient", "010-0000-0000", "12345", "addr1", "addr2"),
                Clock.systemUTC());
    }

    @Test
    @DisplayName("read timeout honored: hung payment-service -> publishOrderPlaced fails within the configured bound")
    void hungPaymentService_failsFastWithinConfiguredReadTimeout() {
        // No response enqueued: MockWebServer accepts the connection but never writes a
        // response body, simulating a hung downstream call.
        OrderEventPublisher publisher = config.standaloneOrderEventPublisher(baseUrl(), 2000, 300);

        long start = System.nanoTime();
        assertThatThrownBy(() -> publisher.publishOrderPlaced(samplePlacedEvent()))
                .isInstanceOf(IllegalStateException.class);
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        // Generous upper bound (10x the configured 300ms read timeout) to absorb CI scheduling
        // jitter while still proving the call did NOT hang indefinitely.
        assertThat(elapsedMs).isLessThan(3000);
    }

    @Test
    @DisplayName("happy path unaffected by the ResilienceClientFactory migration")
    void respondingPaymentService_stillWorks() {
        paymentService.enqueue(new MockResponse().setResponseCode(201));

        OrderEventPublisher publisher = config.standaloneOrderEventPublisher(baseUrl(), 2000, 5000);

        publisher.publishOrderPlaced(samplePlacedEvent());
    }
}
