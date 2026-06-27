package com.example.platform.notification.delivery;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeliveryRecordTest {

    private static final Instant RETRY_AT = Instant.parse("2026-06-28T00:01:00Z");

    private DeliveryRecord pending() {
        return DeliveryRecord.createPending("d-1", "slack", "ops", "t", "b", "{}");
    }

    @Test
    void markSucceeded_pendingToSucceeded_incrementsAttemptAndClearsError() {
        DeliveryRecord rec = pending();

        rec.markSucceeded();

        assertThat(rec.status()).isEqualTo(DeliveryStatus.SUCCEEDED);
        assertThat(rec.attemptCount()).isEqualTo(1);
        assertThat(rec.isTerminal()).isTrue();
        assertThat(rec.scheduledRetryAt()).isEmpty();
        assertThat(rec.lastError()).isEmpty();
    }

    @Test
    void markFailedPermanent_pendingToFailed_immediate() {
        DeliveryRecord rec = pending();

        rec.markFailedPermanent("vendor 404");

        assertThat(rec.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(rec.attemptCount()).isEqualTo(1);
        assertThat(rec.isTerminal()).isTrue();
        assertThat(rec.scheduledRetryAt()).isEmpty();
        assertThat(rec.lastError()).contains("vendor 404");
    }

    @Test
    void markRetryable_underBudget_staysPendingWithScheduledRetry() {
        DeliveryRecord rec = pending();

        rec.markRetryable("503", RETRY_AT);

        assertThat(rec.status()).isEqualTo(DeliveryStatus.PENDING);
        assertThat(rec.attemptCount()).isEqualTo(1);
        assertThat(rec.isTerminal()).isFalse();
        assertThat(rec.scheduledRetryAt()).contains(RETRY_AT);
        assertThat(rec.lastError()).contains("503");
    }

    @Test
    void markRetryable_atBudget_transitionsToFailedAndThrowsExhausted() {
        // maxAttempts 2 -> first retry stays pending, second exhausts.
        DeliveryRecord rec = DeliveryRecord.createPending("d-2", "slack", "ops", "t", "b", "{}", 2);

        rec.markRetryable("503", RETRY_AT); // attempt 1, pending
        assertThat(rec.status()).isEqualTo(DeliveryStatus.PENDING);

        assertThatThrownBy(() -> rec.markRetryable("503 again", RETRY_AT))
                .isInstanceOf(DeliveryRetryExhaustedException.class)
                .satisfies(ex -> {
                    DeliveryRetryExhaustedException e = (DeliveryRetryExhaustedException) ex;
                    assertThat(e.deliveryId()).isEqualTo("d-2");
                    assertThat(e.attempts()).isEqualTo(2);
                });

        // terminal state applied BEFORE the throw (reference contract).
        assertThat(rec.status()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(rec.attemptCount()).isEqualTo(2);
        assertThat(rec.scheduledRetryAt()).isEmpty();
        assertThat(rec.isTerminal()).isTrue();
    }

    @Test
    void terminalRecord_rejectsFurtherTransitions() {
        DeliveryRecord rec = pending();
        rec.markSucceeded();

        assertThatThrownBy(rec::markSucceeded)
                .isInstanceOf(DeliveryStateTransitionInvalidException.class);
        assertThatThrownBy(() -> rec.markFailedPermanent("x"))
                .isInstanceOf(DeliveryStateTransitionInvalidException.class);
        assertThatThrownBy(() -> rec.markRetryable("x", RETRY_AT))
                .isInstanceOf(DeliveryStateTransitionInvalidException.class);
    }

    @Test
    void lastError_isTrimmedTo500Chars() {
        DeliveryRecord rec = pending();
        String huge = "x".repeat(900);

        rec.markFailedPermanent(huge);

        assertThat(rec.lastError().orElseThrow()).hasSize(500);
    }

    @Test
    void reconstructionConstructor_rejectsScheduledRetryOnTerminalState() {
        assertThatThrownBy(() -> new DeliveryRecord(
                "d", "slack", "ops", "t", "b", "{}", 5,
                DeliveryStatus.SUCCEEDED, 1, RETRY_AT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void reconstructionConstructor_rejectsAttemptCountOutOfRange() {
        assertThatThrownBy(() -> new DeliveryRecord(
                "d", "slack", "ops", "t", "b", "{}", 5,
                DeliveryStatus.PENDING, 6, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
