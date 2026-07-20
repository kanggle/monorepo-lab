package com.example.notification.adapter.out.metrics;

import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.NotificationFailureReason;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-533 — asserts the emitted series by NAME and LABELS, because those exact strings are what
 * {@code infra/prometheus/alert-rules.yml} queries. A test that only asserted "a counter exists"
 * would pass against a renamed metric and leave the restored alerts no-data again (F2).
 */
@DisplayName("MicrometerNotificationMetrics 단위 테스트")
class MicrometerNotificationMetricsTest {

    private SimpleMeterRegistry registry;
    private MicrometerNotificationMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new MicrometerNotificationMetrics(registry);
    }

    @Test
    @DisplayName("recordSent 은 notification_sent_total{channel} 을 증가시킨다")
    void recordSent_incrementsSentTotalWithChannelLabel() {
        metrics.recordSent(NotificationChannel.EMAIL);

        Counter counter = registry.find("notification_sent_total").tag("channel", "email").counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("recordFailed 는 notification_failed_total{channel,reason} 을 증가시킨다")
    void recordFailed_incrementsFailedTotalWithChannelAndReasonLabels() {
        metrics.recordFailed(NotificationChannel.EMAIL, NotificationFailureReason.MAIL_SEND);

        Counter counter = registry.find("notification_failed_total")
                .tag("channel", "email")
                .tag("reason", "mail_send")
                .counter();

        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("채널·사유가 다르면 별도 시계열로 분리된다")
    void differentChannelAndReason_separateSeries() {
        metrics.recordFailed(NotificationChannel.EMAIL, NotificationFailureReason.MAIL_AUTH);
        metrics.recordFailed(NotificationChannel.PUSH, NotificationFailureReason.PUSH_DELIVERY);
        metrics.recordFailed(NotificationChannel.PUSH, NotificationFailureReason.PUSH_DELIVERY);

        assertThat(registry.find("notification_failed_total")
                .tag("channel", "email").tag("reason", "mail_auth").counter().count()).isEqualTo(1.0);
        assertThat(registry.find("notification_failed_total")
                .tag("channel", "push").tag("reason", "push_delivery").counter().count()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("null reason 은 unknown 으로 접혀 카디널리티가 유지된다")
    void nullReason_collapsesToUnknown() {
        metrics.recordFailed(NotificationChannel.SMS, null);

        assertThat(registry.find("notification_failed_total")
                .tag("channel", "sms").tag("reason", "unknown").counter().count()).isEqualTo(1.0);
    }
}
