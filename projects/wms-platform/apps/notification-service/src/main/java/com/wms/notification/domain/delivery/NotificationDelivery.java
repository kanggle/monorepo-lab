package com.wms.notification.domain.delivery;

import com.wms.notification.domain.error.DeliveryRetryExhaustedException;
import com.wms.notification.domain.error.DeliveryStateTransitionInvalidException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Write-shaped aggregate representing one logical delivery (one channel ×
 * one event). Spans queue-time to terminal {@code SUCCEEDED}/{@code FAILED}.
 *
 * <h2>State machine</h2>
 *
 * <pre>
 *   PENDING ─[markSucceeded()]─→ SUCCEEDED
 *   PENDING ─[markRetryable(), attempt < max]─→ PENDING
 *   PENDING ─[markFailedPermanent() OR markRetryable() with attempt == max]─→ FAILED
 * </pre>
 *
 * <p>Terminal states are immutable — any further transition raises
 * {@link DeliveryStateTransitionInvalidException}.
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@code attemptCount &le; maxAttempts} (5 in v1).</li>
 *   <li>{@code scheduledRetryAt} only set when {@code status = PENDING}
 *       AND {@code attemptCount > 0}.</li>
 *   <li>{@code payloadSnapshot} is immutable after creation — guarantees
 *       retry attempts produce the same Slack message.</li>
 * </ul>
 */
public final class NotificationDelivery {

    public static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final int LAST_ERROR_MAX_LENGTH = 500;

    private final UUID id;
    private final UUID eventId;
    private final String sourceTopic;
    private final String channelId;
    private final String recipient;
    private final String deliveryIdempotencyKey;
    private final String payloadSnapshot;
    private final int maxAttempts;
    private final Instant createdAt;

    private DeliveryStatus status;
    private int attemptCount;
    private Instant scheduledRetryAt;
    private String lastError;
    private int version;
    private Instant updatedAt;

    /** Constructor used by application services for new rows. */
    public static NotificationDelivery createPending(UUID id,
                                                     UUID eventId,
                                                     String sourceTopic,
                                                     String channelId,
                                                     String recipient,
                                                     String deliveryIdempotencyKey,
                                                     String payloadSnapshot,
                                                     Instant now) {
        return new NotificationDelivery(
                id, eventId, sourceTopic, channelId, recipient,
                deliveryIdempotencyKey, payloadSnapshot,
                DEFAULT_MAX_ATTEMPTS,
                DeliveryStatus.PENDING,
                0, null, null, 0,
                now, now);
    }

    /** Reconstruction constructor used by the persistence adapter. */
    public NotificationDelivery(UUID id,
                                UUID eventId,
                                String sourceTopic,
                                String channelId,
                                String recipient,
                                String deliveryIdempotencyKey,
                                String payloadSnapshot,
                                int maxAttempts,
                                DeliveryStatus status,
                                int attemptCount,
                                Instant scheduledRetryAt,
                                String lastError,
                                int version,
                                Instant createdAt,
                                Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.sourceTopic = Objects.requireNonNull(sourceTopic, "sourceTopic");
        this.channelId = Objects.requireNonNull(channelId, "channelId");
        this.recipient = Objects.requireNonNull(recipient, "recipient");
        this.deliveryIdempotencyKey = Objects.requireNonNull(deliveryIdempotencyKey, "deliveryIdempotencyKey");
        this.payloadSnapshot = Objects.requireNonNull(payloadSnapshot, "payloadSnapshot");
        this.maxAttempts = maxAttempts;
        this.status = Objects.requireNonNull(status, "status");
        this.attemptCount = attemptCount;
        this.scheduledRetryAt = scheduledRetryAt;
        this.lastError = lastError;
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be > 0");
        }
        if (attemptCount < 0 || attemptCount > maxAttempts) {
            throw new IllegalArgumentException(
                    "attemptCount must be in [0, " + maxAttempts + "], got " + attemptCount);
        }
        if (status != DeliveryStatus.PENDING && scheduledRetryAt != null) {
            throw new IllegalArgumentException(
                    "scheduledRetryAt may only be set while PENDING");
        }
    }

    /**
     * The vendor accepted the message. Transitions PENDING → SUCCEEDED. Any
     * other source state is rejected as
     * {@link DeliveryStateTransitionInvalidException}.
     */
    public void markSucceeded(Instant now) {
        ensurePending(DeliveryStatus.SUCCEEDED);
        attemptCount++;
        status = DeliveryStatus.SUCCEEDED;
        scheduledRetryAt = null;
        lastError = null;
        version++;
        updatedAt = now;
    }

    /**
     * The vendor returned a transient failure. Either schedules another
     * retry (PENDING → PENDING with {@code scheduledRetryAt} set) or
     * exhausts the budget and fails permanently (PENDING → FAILED).
     *
     * @param error      vendor error trimmed to ≤ 500 chars
     * @param backoff    next attempt delay; ignored on exhaustion
     * @param now        current time
     */
    public void markRetryable(String error, java.time.Duration backoff, Instant now) {
        ensurePending(DeliveryStatus.PENDING);
        attemptCount++;
        lastError = trim(error);
        if (attemptCount >= maxAttempts) {
            status = DeliveryStatus.FAILED;
            scheduledRetryAt = null;
            version++;
            updatedAt = now;
            throw new DeliveryRetryExhaustedException(id, attemptCount);
        }
        Objects.requireNonNull(backoff, "backoff");
        scheduledRetryAt = now.plus(backoff);
        version++;
        updatedAt = now;
    }

    /**
     * The vendor returned a permanent error (e.g. Slack 4xx, channel not
     * configured). Transitions PENDING → FAILED without consuming further
     * retries.
     */
    public void markFailedPermanent(String error, Instant now) {
        ensurePending(DeliveryStatus.FAILED);
        attemptCount++;
        status = DeliveryStatus.FAILED;
        scheduledRetryAt = null;
        lastError = trim(error);
        version++;
        updatedAt = now;
    }

    private void ensurePending(DeliveryStatus to) {
        if (status != DeliveryStatus.PENDING) {
            throw new DeliveryStateTransitionInvalidException(status, to);
        }
    }

    private static String trim(String s) {
        if (s == null) {
            return null;
        }
        return s.length() > LAST_ERROR_MAX_LENGTH ? s.substring(0, LAST_ERROR_MAX_LENGTH) : s;
    }

    /** {@code true} when the delivery has reached a terminal state. */
    public boolean isTerminal() {
        return status.isTerminal();
    }

    public UUID id() { return id; }
    public UUID eventId() { return eventId; }
    public String sourceTopic() { return sourceTopic; }
    public String channelId() { return channelId; }
    public String recipient() { return recipient; }
    public String deliveryIdempotencyKey() { return deliveryIdempotencyKey; }
    public String payloadSnapshot() { return payloadSnapshot; }
    public int maxAttempts() { return maxAttempts; }
    public DeliveryStatus status() { return status; }
    public int attemptCount() { return attemptCount; }
    public Optional<Instant> scheduledRetryAt() { return Optional.ofNullable(scheduledRetryAt); }
    public Optional<String> lastError() { return Optional.ofNullable(lastError); }
    public int version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
