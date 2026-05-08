package com.wms.notification.domain.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.notification.domain.error.DeliveryRetryExhaustedException;
import com.wms.notification.domain.error.DeliveryStateTransitionInvalidException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Exercises every legal transition + every illegal source state
 * (architecture.md § State Machine). Required by Testing Requirements.
 */
class NotificationDeliveryStateMachineTest {

    private static final Instant NOW = Instant.parse("2025-01-01T00:00:00Z");

    @Test
    void pendingToSucceeded() {
        NotificationDelivery d = newPending();
        d.markSucceeded(NOW);
        assertThat(d.status()).isEqualTo(DeliveryStatus.SUCCEEDED);
        assertThat(d.attemptCount()).isEqualTo(1);
        assertThat(d.scheduledRetryAt()).isEmpty();
        assertThat(d.lastError()).isEmpty();
        assertThat(d.isTerminal()).isTrue();
    }

    @Test
    void pendingToPendingOnRetryable() {
        NotificationDelivery d = newPending();
        d.markRetryable("vendor 503", Duration.ofSeconds(5), NOW);
        assertThat(d.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(d.attemptCount()).isEqualTo(1);
        assertThat(d.scheduledRetryAt()).contains(NOW.plusSeconds(5));
        assertThat(d.lastError()).contains("vendor 503");
    }

    @Test
    void pendingToFailedOnExhaustion() {
        NotificationDelivery d = newPending();
        // Drive 4 successful retries, then the 5th must transition to FAILED.
        for (int i = 0; i < 4; i++) {
            d.markRetryable("transient", Duration.ofSeconds(1), NOW);
        }
        assertThatThrownBy(() ->
                d.markRetryable("transient", Duration.ofSeconds(1), NOW))
                .isInstanceOf(DeliveryRetryExhaustedException.class);
        assertThat(d.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(d.attemptCount()).isEqualTo(5);
        assertThat(d.scheduledRetryAt()).isEmpty();
        assertThat(d.isTerminal()).isTrue();
    }

    @Test
    void pendingToFailedOnPermanent() {
        NotificationDelivery d = newPending();
        d.markFailedPermanent("Slack 404", NOW);
        assertThat(d.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(d.attemptCount()).isEqualTo(1);
        assertThat(d.scheduledRetryAt()).isEmpty();
        assertThat(d.lastError()).contains("Slack 404");
        assertThat(d.isTerminal()).isTrue();
    }

    @Test
    void succeededToAnything_rejected() {
        NotificationDelivery d = newPending();
        d.markSucceeded(NOW);
        assertThatThrownBy(() -> d.markSucceeded(NOW))
                .isInstanceOf(DeliveryStateTransitionInvalidException.class);
        assertThatThrownBy(() -> d.markRetryable("x", Duration.ofSeconds(1), NOW))
                .isInstanceOf(DeliveryStateTransitionInvalidException.class);
        assertThatThrownBy(() -> d.markFailedPermanent("x", NOW))
                .isInstanceOf(DeliveryStateTransitionInvalidException.class);
    }

    @Test
    void failedToAnything_rejected() {
        NotificationDelivery d = newPending();
        d.markFailedPermanent("permanent", NOW);
        assertThatThrownBy(() -> d.markSucceeded(NOW))
                .isInstanceOf(DeliveryStateTransitionInvalidException.class);
        assertThatThrownBy(() -> d.markRetryable("x", Duration.ofSeconds(1), NOW))
                .isInstanceOf(DeliveryStateTransitionInvalidException.class);
        assertThatThrownBy(() -> d.markFailedPermanent("x", NOW))
                .isInstanceOf(DeliveryStateTransitionInvalidException.class);
    }

    @Test
    void succeededIncrementsAttemptCount() {
        NotificationDelivery d = newPending();
        d.markRetryable("transient", Duration.ofSeconds(1), NOW); // attempt = 1
        d.markRetryable("transient", Duration.ofSeconds(1), NOW); // attempt = 2
        d.markSucceeded(NOW);
        assertThat(d.attemptCount()).isEqualTo(3);
    }

    @Test
    void retryableTrimsLastErrorTo500Chars() {
        NotificationDelivery d = newPending();
        String huge = "x".repeat(2_000);
        d.markRetryable(huge, Duration.ofSeconds(1), NOW);
        assertThat(d.lastError()).hasValueSatisfying(s -> assertThat(s).hasSize(500));
    }

    @Test
    void permanentFailureClearsScheduledRetry() {
        NotificationDelivery d = newPending();
        d.markRetryable("transient", Duration.ofSeconds(5), NOW);
        assertThat(d.scheduledRetryAt()).isPresent();
        d.markFailedPermanent("Slack 410 token revoked", NOW);
        assertThat(d.scheduledRetryAt()).isEmpty();
    }

    @Test
    void versionIncrementsOnEveryTransition() {
        NotificationDelivery d = newPending();
        int v0 = d.version();
        d.markRetryable("transient", Duration.ofSeconds(1), NOW);
        assertThat(d.version()).isEqualTo(v0 + 1);
        d.markSucceeded(NOW);
        assertThat(d.version()).isEqualTo(v0 + 2);
    }

    @Test
    void reconstructionRejectsScheduledRetryWhenTerminal() {
        assertThatThrownBy(() -> new NotificationDelivery(
                UUID.randomUUID(), UUID.randomUUID(), "topic", "ch", "ch",
                "key", "{}", 5,
                DeliveryStatus.SUCCEEDED, 1,
                NOW, // scheduledRetryAt — illegal alongside SUCCEEDED
                null, 1, NOW, NOW))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("scheduledRetryAt");
    }

    @Test
    void createPendingHasZeroAttemptsAndPendingStatus() {
        NotificationDelivery d = newPending();
        assertThat(d.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(d.attemptCount()).isZero();
        assertThat(d.scheduledRetryAt()).isEmpty();
        assertThat(d.lastError()).isEmpty();
        assertThat(d.maxAttempts()).isEqualTo(NotificationDelivery.DEFAULT_MAX_ATTEMPTS);
    }

    private static NotificationDelivery newPending() {
        return NotificationDelivery.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "wms.inventory.alert.v1",
                "wms-alerts",
                "wms-alerts",
                "abc123",
                "{}",
                NOW);
    }
}
