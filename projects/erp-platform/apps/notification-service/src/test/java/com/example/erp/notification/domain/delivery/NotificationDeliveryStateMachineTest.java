package com.example.erp.notification.domain.delivery;

import com.example.erp.notification.domain.error.DeliveryStateTransitionInvalidException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Delivery state machine: IN_APP synchronous DELIVERED + terminal immutability + v2 retry. */
class NotificationDeliveryStateMachineTest {

    private final Instant now = Instant.parse("2026-06-05T10:00:00Z");

    private NotificationDelivery pending(DeliveryChannel channel) {
        return NotificationDelivery.createPending("dlv-1", "erp", "ntf-1", "evt-1", channel, now);
    }

    @Test
    void createPendingExternalIsImmediatelyDue() {
        // TASK-ERP-BE-020: external delivery is created PENDING + due now (scheduledRetryAt set),
        // unlike the IN_APP createPending (no scheduledRetryAt).
        NotificationDelivery d = NotificationDelivery.createPendingExternal(
                "dlv-2", "erp", "ntf-1", "evt-1", DeliveryChannel.SLACK, now);
        assertThat(d.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(d.attemptCount()).isZero();
        assertThat(d.scheduledRetryAt()).contains(now);
        assertThat(d.isTerminal()).isFalse();
    }

    @Test
    void inAppDeliversSynchronouslyWithAttemptCountOne() {
        NotificationDelivery d = pending(DeliveryChannel.IN_APP);
        d.markDelivered(now);
        assertThat(d.status()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(d.attemptCount()).isEqualTo(1);
        assertThat(d.scheduledRetryAt()).isEmpty();
        assertThat(d.isTerminal()).isTrue();
    }

    @Test
    void deliveredIsTerminalAndImmutable() {
        NotificationDelivery d = pending(DeliveryChannel.IN_APP);
        d.markDelivered(now);
        assertThatThrownBy(() -> d.markDelivered(now))
                .isInstanceOf(DeliveryStateTransitionInvalidException.class);
        assertThatThrownBy(() -> d.markFailed("x", now))
                .isInstanceOf(DeliveryStateTransitionInvalidException.class);
    }

    @Test
    void retryableSchedulesUntilBudgetExhausted() {
        // v2 path: 5-attempt budget → FAILED at the 5th.
        NotificationDelivery d = pending(DeliveryChannel.SLACK);
        for (int i = 1; i <= 4; i++) {
            d.markRetryable("transient", Duration.ofSeconds(1), now);
            assertThat(d.status()).isEqualTo(DeliveryStatus.PENDING);
            assertThat(d.scheduledRetryAt()).isPresent();
        }
        d.markRetryable("transient", Duration.ofSeconds(1), now);
        assertThat(d.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(d.attemptCount()).isEqualTo(5);
        assertThat(d.scheduledRetryAt()).isEmpty();
    }

    @Test
    void markFailedIsTerminal() {
        NotificationDelivery d = pending(DeliveryChannel.SLACK);
        d.markFailed("permanent", now);
        assertThat(d.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(d.lastError()).contains("permanent");
        assertThatThrownBy(() -> d.markDelivered(now))
                .isInstanceOf(DeliveryStateTransitionInvalidException.class);
    }
}
