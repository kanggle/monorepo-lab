package com.example.erp.notification.application;

import com.example.erp.notification.application.port.outbound.NotificationChannelPort;
import com.example.erp.notification.application.port.outbound.NotificationChannelPort.DeliveryOutcome;
import com.example.erp.notification.application.port.outbound.NotificationMetricsPort;
import com.example.erp.notification.domain.delivery.DeliveryChannel;
import com.example.erp.notification.domain.delivery.DeliveryStatus;
import com.example.erp.notification.domain.delivery.NotificationDelivery;
import com.example.erp.notification.domain.delivery.repository.NotificationDeliveryRepository;
import com.example.erp.notification.domain.notification.Notification;
import com.example.erp.notification.domain.notification.NotificationType;
import com.example.erp.notification.domain.notification.SourceRef;
import com.example.erp.notification.domain.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class DeliveryAttemptProcessorTest {

    @Mock NotificationDeliveryRepository deliveryRepository;
    @Mock NotificationRepository notificationRepository;
    @Mock NotificationMetricsPort metrics;
    @Mock NotificationChannelPort slackChannel;

    private DeliveryAttemptProcessor processor;

    private final Instant now = Instant.parse("2026-06-12T00:00:00Z");

    @BeforeEach
    void setUp() {
        // deterministic backoff (factor 1.0) — base == jittered.
        RandomGenerator fixedHalf = new RandomGenerator() {
            @Override public long nextLong() { return 0L; }
            @Override public double nextDouble() { return 0.5; }
        };
        RetryBackoffPolicy backoff = new RetryBackoffPolicy(1000, 60000, fixedHalf);
        processor = new DeliveryAttemptProcessor(
                deliveryRepository, notificationRepository, backoff, metrics, List.of(slackChannel));
    }

    private Notification notification() {
        return Notification.create("ntf-1", "erp", "emp-1",
                NotificationType.APPROVAL_SUBMITTED, "title", "body",
                SourceRef.approval("appr-1"), now);
    }

    private NotificationDelivery freshExternal() {
        return NotificationDelivery.createPendingExternal(
                "dlv-1", "erp", "ntf-1", "evt-1", DeliveryChannel.SLACK, now);
    }

    private void stubLoad(NotificationDelivery delivery) {
        when(deliveryRepository.findById("dlv-1")).thenReturn(Optional.of(delivery));
        when(notificationRepository.findByIdInternal("erp", "ntf-1"))
                .thenReturn(Optional.of(notification()));
        when(slackChannel.channel()).thenReturn(DeliveryChannel.SLACK);
    }

    @Test
    void on2xx_marksDelivered() {
        NotificationDelivery delivery = freshExternal();
        stubLoad(delivery);
        when(slackChannel.deliver(any())).thenReturn(DeliveryOutcome.ofDelivered());

        processor.attempt("dlv-1", now);

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.attemptCount()).isEqualTo(1);
        verify(deliveryRepository).save(delivery);
        verify(metrics).deliveryStatus(DeliveryStatus.DELIVERED);
    }

    @Test
    void onTransientFailure_marksRetryable_withBackoff() {
        NotificationDelivery delivery = freshExternal();
        stubLoad(delivery);
        when(slackChannel.deliver(any())).thenReturn(DeliveryOutcome.failed("slack 503"));

        processor.attempt("dlv-1", now);

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(delivery.attemptCount()).isEqualTo(1);
        assertThat(delivery.scheduledRetryAt()).contains(now.plusMillis(1000)); // backoffFor(1)
        verify(deliveryRepository).save(delivery);
        verify(metrics, never()).deliveryStatus(any());
    }

    @Test
    void atRetryCap_marksFailed_withExhaustedReason() {
        // attemptCount 4 (one below the cap of 5), PENDING + due.
        NotificationDelivery delivery = new NotificationDelivery(
                "dlv-1", "erp", "ntf-1", "evt-1", DeliveryChannel.SLACK,
                NotificationDelivery.DEFAULT_MAX_ATTEMPTS, DeliveryStatus.PENDING, 4,
                now, "slack 503", 4, now, now);
        stubLoad(delivery);
        when(slackChannel.deliver(any())).thenReturn(DeliveryOutcome.failed("slack 503 again"));

        processor.attempt("dlv-1", now);

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.attemptCount()).isEqualTo(5);
        assertThat(delivery.lastError()).get().asString().startsWith("DELIVERY_RETRY_EXHAUSTED");
        verify(metrics).deliveryStatus(DeliveryStatus.FAILED);
    }

    @Test
    void missingNotification_marksFailed_withoutCallingChannel() {
        NotificationDelivery delivery = freshExternal();
        when(deliveryRepository.findById("dlv-1")).thenReturn(Optional.of(delivery));
        when(notificationRepository.findByIdInternal("erp", "ntf-1")).thenReturn(Optional.empty());

        processor.attempt("dlv-1", now);

        assertThat(delivery.status()).isEqualTo(DeliveryStatus.FAILED);
        verify(slackChannel, never()).deliver(any());
        verify(metrics).deliveryStatus(DeliveryStatus.FAILED);
    }

    @Test
    void terminalDelivery_isSkipped() {
        NotificationDelivery delivered = new NotificationDelivery(
                "dlv-1", "erp", "ntf-1", "evt-1", DeliveryChannel.SLACK,
                NotificationDelivery.DEFAULT_MAX_ATTEMPTS, DeliveryStatus.DELIVERED, 1,
                null, null, 1, now, now);
        when(deliveryRepository.findById("dlv-1")).thenReturn(Optional.of(delivered));

        processor.attempt("dlv-1", now);

        verify(deliveryRepository, never()).save(any());
        verify(slackChannel, never()).deliver(any());
    }

    @Test
    void missingDelivery_isNoOp() {
        when(deliveryRepository.findById("dlv-1")).thenReturn(Optional.empty());

        processor.attempt("dlv-1", now);

        verify(deliveryRepository, never()).save(any());
    }
}
