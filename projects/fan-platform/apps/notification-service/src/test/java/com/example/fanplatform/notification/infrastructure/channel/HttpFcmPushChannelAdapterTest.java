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
 * Unit test for {@link HttpFcmPushChannelAdapter} (TASK-FAN-BE-017): it maps an FCM
 * HTTP v1 2xx to a delivered message-name (sending the bearer header + the
 * topic-targeted body), sanitizes the topic, and is best-effort/never-throw on every
 * failure mode (5xx, server down, 2xx without a name). RestClient is pointed at a
 * MockWebServer so no real FCM endpoint is contacted.
 */
class HttpFcmPushChannelAdapterTest {

    private MockWebServer fcm;
    private SimpleMeterRegistry registry;

    @BeforeEach
    void setUp() throws IOException {
        fcm = new MockWebServer();
        fcm.start();
        registry = new SimpleMeterRegistry();
    }

    @AfterEach
    void tearDown() {
        try {
            fcm.shutdown();
        } catch (Exception ignored) {
            // already shut down (the provider-down test shuts it down itself)
        }
    }

    private String baseUrl() {
        return "http://" + fcm.getHostName() + ":" + fcm.getPort();
    }

    private HttpFcmPushChannelAdapter adapter(String baseUrl) {
        return new HttpFcmPushChannelAdapter(registry, baseUrl, "test-project",
                "test-fcm-key", "fan_", 2000, 5000);
    }

    private static Notification sample(String accountId) {
        return Notification.create("n1", "fan-platform", accountId, NotificationType.WELCOME,
                "Welcome to GOLD membership", "window 2026-06-01 … 2026-09-01", "evt-1",
                "fan.membership.activated", "mem-1", Instant.parse("2026-06-11T08:00:00Z"));
    }

    private double counter(String outcome) {
        return registry.counter("notification_channel_deliveries_total",
                "channel", "PUSH", "outcome", outcome).count();
    }

    @Test
    void deliversOn2xx_mapsMessageName_sendsBearerAndTopicBody() throws Exception {
        fcm.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"name\":\"projects/test-project/messages/abc123\"}"));

        DeliveryResult result = adapter(baseUrl()).deliver(sample("acc-1"));

        assertThat(result.delivered()).isTrue();
        assertThat(result.channel()).isEqualTo("PUSH");
        assertThat(result.ref()).isEqualTo("projects/test-project/messages/abc123");
        assertThat(counter("delivered")).isEqualTo(1.0);

        RecordedRequest req = fcm.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/v1/projects/test-project/messages:send");
        assertThat(req.getHeader("Authorization")).isEqualTo("Bearer test-fcm-key");
        String body = req.getBody().readUtf8();
        assertThat(body).contains("\"topic\":\"fan_acc-1\"");
        assertThat(body).contains("\"notification\":");
        assertThat(body).contains("\"title\":\"Welcome to GOLD membership\"");
    }

    @Test
    void sanitizesTopicForNonUuidAccount() throws Exception {
        fcm.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"name\":\"projects/test-project/messages/x\"}"));

        adapter(baseUrl()).deliver(sample("ac/c 1"));

        RecordedRequest req = fcm.takeRequest();
        assertThat(req.getBody().readUtf8()).contains("\"topic\":\"fan_ac_c_1\"");
    }

    @Test
    void serverError_returnsFailed_neverThrows() {
        fcm.enqueue(new MockResponse().setResponseCode(503));

        DeliveryResult[] result = new DeliveryResult[1];
        assertThatCode(() -> result[0] = adapter(baseUrl()).deliver(sample("acc-1")))
                .doesNotThrowAnyException();

        assertThat(result[0].delivered()).isFalse();
        assertThat(result[0].ref()).isNull();
        assertThat(counter("failed")).isEqualTo(1.0);
        assertThat(counter("delivered")).isEqualTo(0.0);
    }

    @Test
    void twoXxWithoutName_returnsFailed() {
        fcm.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"messageId\":\"123\"}"));

        DeliveryResult result = adapter(baseUrl()).deliver(sample("acc-1"));

        assertThat(result.delivered()).isFalse();
        assertThat(counter("failed")).isEqualTo(1.0);
    }

    @Test
    void providerDown_returnsFailed_neverThrows() throws IOException {
        String baseUrl = baseUrl();
        fcm.shutdown(); // connection refused on the next call

        DeliveryResult[] result = new DeliveryResult[1];
        assertThatCode(() -> result[0] = adapter(baseUrl).deliver(sample("acc-1")))
                .doesNotThrowAnyException();

        assertThat(result[0].delivered()).isFalse();
        assertThat(counter("failed")).isEqualTo(1.0);
    }
}
