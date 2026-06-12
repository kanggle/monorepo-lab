package com.example.fanplatform.notification.infrastructure.channel;

import com.example.fanplatform.notification.domain.channel.NotificationChannelPort.DeliveryResult;
import com.example.fanplatform.notification.domain.notification.Notification;
import com.example.fanplatform.notification.domain.notification.NotificationType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Unit test for {@link HttpEmailChannelAdapter} (TASK-FAN-BE-016): it maps a
 * provider 2xx to a delivered ref (sending the API-key header + the synthetic
 * recipient body), and is best-effort/never-throw on every failure mode (5xx,
 * provider down, 2xx without a usable id) — each recording the outcome counter.
 * RestClient is pointed at a MockWebServer so no real provider is contacted.
 */
class HttpEmailChannelAdapterTest {

    private MockWebServer provider;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() throws IOException {
        provider = new MockWebServer();
        provider.start();
        registry = new SimpleMeterRegistry();
    }

    @AfterEach
    void tearDown() {
        try {
            provider.shutdown();
        } catch (Exception ignored) {
            // already shut down (the provider-down test shuts it down itself)
        }
    }

    private String baseUrl() {
        return "http://" + provider.getHostName() + ":" + provider.getPort();
    }

    private HttpEmailChannelAdapter adapter(String baseUrl) {
        return new HttpEmailChannelAdapter(registry, baseUrl, "test-api-key",
                "no-reply@fan-platform.example", "fan-platform.example", 2000, 5000);
    }

    private static Notification sample() {
        return Notification.create("n1", "fan-platform", "acc-1", NotificationType.WELCOME,
                "Welcome to GOLD membership", "window 2026-06-01 … 2026-09-01", "evt-1",
                "fan.membership.activated", "mem-1", Instant.parse("2026-06-11T08:00:00Z"));
    }

    private double counter(String outcome) {
        return registry.counter("notification_channel_deliveries_total",
                "channel", "EMAIL", "outcome", outcome).count();
    }

    @Test
    void deliversOn2xx_mapsRef_sendsAuthHeaderAndSyntheticRecipient() throws Exception {
        provider.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"id\":\"prov-ref-123\"}"));

        DeliveryResult result = adapter(baseUrl()).deliver(sample());

        assertThat(result.delivered()).isTrue();
        assertThat(result.channel()).isEqualTo("EMAIL");
        assertThat(result.ref()).isEqualTo("prov-ref-123");
        assertThat(counter("delivered")).isEqualTo(1.0);

        RecordedRequest req = provider.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/emails");
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer test-api-key");
        String body = req.getBody().readUtf8();
        assertThat(body).contains("\"to\":\"acc-1@fan-platform.example\"");
        assertThat(body).contains("\"from\":\"no-reply@fan-platform.example\"");
        assertThat(body).contains("\"subject\":\"Welcome to GOLD membership\"");
    }

    @Test
    void serverError_returnsFailed_neverThrows() {
        provider.enqueue(new MockResponse().setResponseCode(503));

        DeliveryResult[] result = new DeliveryResult[1];
        assertThatCode(() -> result[0] = adapter(baseUrl()).deliver(sample()))
                .doesNotThrowAnyException();

        assertThat(result[0].delivered()).isFalse();
        assertThat(result[0].ref()).isNull();
        assertThat(counter("failed")).isEqualTo(1.0);
        assertThat(counter("delivered")).isEqualTo(0.0);
    }

    @Test
    void twoXxWithoutUsableId_returnsFailed() {
        provider.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"status\":\"queued\"}"));

        DeliveryResult result = adapter(baseUrl()).deliver(sample());

        assertThat(result.delivered()).isFalse();
        assertThat(counter("failed")).isEqualTo(1.0);
    }

    @Test
    void providerDown_returnsFailed_neverThrows() throws IOException {
        String baseUrl = baseUrl();
        provider.shutdown(); // connection refused on the next call

        DeliveryResult[] result = new DeliveryResult[1];
        assertThatCode(() -> result[0] = adapter(baseUrl).deliver(sample()))
                .doesNotThrowAnyException();

        assertThat(result[0].delivered()).isFalse();
        assertThat(counter("failed")).isEqualTo(1.0);
    }
}
