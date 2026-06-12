package com.example.erp.notification.infrastructure.channel;

import com.example.erp.notification.application.port.outbound.NotificationChannelPort.DeliveryOutcome;
import com.example.erp.notification.config.ExternalNotificationProperties;
import com.example.erp.notification.domain.delivery.DeliveryChannel;
import com.example.erp.notification.domain.notification.Notification;
import com.example.erp.notification.domain.notification.NotificationType;
import com.example.erp.notification.domain.notification.SourceRef;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class SlackWebhookChannelAdapterTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() {
        try {
            server.shutdown();
        } catch (Exception ignored) {
            // already shut down by a test exercising the connection-refused path
        }
    }

    private SlackWebhookChannelAdapter adapterPointingAt(String url) {
        ExternalNotificationProperties props = new ExternalNotificationProperties();
        props.setMode("slack");
        props.getSlack().setWebhookUrl(url);
        props.getSlack().setConnectTimeoutMs(1000);
        props.getSlack().setReadTimeoutMs(1000);
        return new SlackWebhookChannelAdapter(props);
    }

    private Notification notification() {
        return Notification.create("ntf-1", "erp", "emp-1",
                NotificationType.APPROVAL_SUBMITTED, "결재 요청 도착", "본문",
                SourceRef.approval("appr-1"), Instant.parse("2026-06-12T00:00:00Z"));
    }

    @Test
    void on2xx_reportsDelivered_andPostsJsonText() throws Exception {
        server.enqueue(new MockResponse().setResponseCode(200).setBody("ok"));

        DeliveryOutcome outcome = adapterPointingAt(server.url("/").toString()).deliver(notification());

        assertThat(outcome.delivered()).isTrue();
        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getHeader("Content-Type")).contains("application/json");
        assertThat(req.getBody().readUtf8()).contains("text").contains("결재 요청 도착");
    }

    @Test
    void on5xx_reportsNotDelivered_neverThrows() {
        server.enqueue(new MockResponse().setResponseCode(500));

        DeliveryOutcome outcome = adapterPointingAt(server.url("/").toString()).deliver(notification());

        assertThat(outcome.delivered()).isFalse();
        assertThat(outcome.detail()).isNotBlank();
    }

    @Test
    void onConnectionRefused_reportsNotDelivered_neverThrows() throws Exception {
        String deadUrl = server.url("/").toString();
        server.shutdown(); // nothing listening → transport error

        SlackWebhookChannelAdapter adapter = adapterPointingAt(deadUrl);
        assertThatCode(() -> {
            DeliveryOutcome outcome = adapter.deliver(notification());
            assertThat(outcome.delivered()).isFalse();
        }).doesNotThrowAnyException();
    }

    @Test
    void channelIsSlack() {
        assertThat(adapterPointingAt(server.url("/").toString()).channel())
                .isEqualTo(DeliveryChannel.SLACK);
    }
}
