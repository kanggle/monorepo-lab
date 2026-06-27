package com.example.platform.notification.delivery;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Write-shaped delivery record — one logical delivery (one channel × one event),
 * spanning queue-time to a terminal {@link DeliveryStatus#SUCCEEDED}/{@link DeliveryStatus#FAILED}.
 *
 * <p>This is the lib-owned generalisation of the wms Category-C reference
 * ({@code com.wms.notification.domain.delivery.NotificationDelivery}). It carries
 * only the channel-agnostic delivery lifecycle; the service supplies the rest of
 * the row (its own JPA entity, its idempotency key, its source topic, etc.) and
 * maps to/from this value. The id is an opaque {@code String} so the lib does not
 * constrain the service's id type (UUID, etc.).
 *
 * <h2>State machine (preserves the wms semantics)</h2>
 * <pre>
 *   PENDING ─[markSucceeded()]─────────────────────────────────────→ SUCCEEDED
 *   PENDING ─[markRetryable(),         attemptCount &lt; maxAttempts]──→ PENDING (scheduledRetryAt set)
 *   PENDING ─[markRetryable(),         attemptCount == maxAttempts]──→ FAILED  (throws DeliveryRetryExhaustedException)
 *   PENDING ─[markFailedPermanent()]───────────────────────────────→ FAILED
 * </pre>
 *
 * <p>Terminal states are immutable — any further transition raises
 * {@link DeliveryStateTransitionInvalidException}.
 *
 * <h2>Invariants (lifted verbatim from the reference)</h2>
 * <ul>
 *   <li>{@code attemptCount} stays in {@code [0, maxAttempts]}.</li>
 *   <li>{@code scheduledRetryAt} is set only while {@code PENDING} with {@code attemptCount > 0}.</li>
 *   <li>{@code lastError} is trimmed to {@value #LAST_ERROR_MAX_LENGTH} chars.</li>
 *   <li>terminal records reject every further transition.</li>
 * </ul>
 */
public final class DeliveryRecord {

    public static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final int LAST_ERROR_MAX_LENGTH = 500;

    private final String id;
    private final String channel;
    private final String recipient;
    private final String title;
    private final String body;
    private final String payloadJson;
    private final int maxAttempts;

    private DeliveryStatus status;
    private int attemptCount;
    private Instant scheduledRetryAt;
    private String lastError;

    /** Factory for a brand-new pending row (first attempt not yet made). */
    public static DeliveryRecord createPending(String id,
                                               String channel,
                                               String recipient,
                                               String title,
                                               String body,
                                               String payloadJson) {
        return new DeliveryRecord(id, channel, recipient, title, body, payloadJson,
                DEFAULT_MAX_ATTEMPTS, DeliveryStatus.PENDING, 0, null, null);
    }

    /** Factory for a brand-new pending row with a custom retry budget. */
    public static DeliveryRecord createPending(String id,
                                               String channel,
                                               String recipient,
                                               String title,
                                               String body,
                                               String payloadJson,
                                               int maxAttempts) {
        return new DeliveryRecord(id, channel, recipient, title, body, payloadJson,
                maxAttempts, DeliveryStatus.PENDING, 0, null, null);
    }

    /** Reconstruction constructor used by the service's persistence adapter. */
    public DeliveryRecord(String id,
                          String channel,
                          String recipient,
                          String title,
                          String body,
                          String payloadJson,
                          int maxAttempts,
                          DeliveryStatus status,
                          int attemptCount,
                          Instant scheduledRetryAt,
                          String lastError) {
        this.id = Objects.requireNonNull(id, "id");
        this.channel = Objects.requireNonNull(channel, "channel");
        this.recipient = Objects.requireNonNull(recipient, "recipient");
        this.title = title;
        this.body = body;
        this.payloadJson = payloadJson;
        this.maxAttempts = maxAttempts;
        this.status = Objects.requireNonNull(status, "status");
        this.attemptCount = attemptCount;
        this.scheduledRetryAt = scheduledRetryAt;
        this.lastError = lastError;
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be > 0");
        }
        if (attemptCount < 0 || attemptCount > maxAttempts) {
            throw new IllegalArgumentException(
                    "attemptCount must be in [0, " + maxAttempts + "], got " + attemptCount);
        }
        if (status != DeliveryStatus.PENDING && scheduledRetryAt != null) {
            throw new IllegalArgumentException("scheduledRetryAt may only be set while PENDING");
        }
    }

    /**
     * The vendor accepted the message. PENDING → SUCCEEDED. Any other source state
     * is rejected as {@link DeliveryStateTransitionInvalidException}.
     */
    public void markSucceeded() {
        ensurePending(DeliveryStatus.SUCCEEDED);
        attemptCount++;
        status = DeliveryStatus.SUCCEEDED;
        scheduledRetryAt = null;
        lastError = null;
    }

    /**
     * A transient failure. Either schedules another retry (PENDING → PENDING with
     * {@code scheduledRetryAt} set) or, when the budget is exhausted, transitions to
     * terminal FAILED and throws {@link DeliveryRetryExhaustedException} (the terminal
     * state is applied <b>before</b> the throw so the caller can persist it on catch —
     * the wms reference contract).
     *
     * @param error       vendor error (trimmed to ≤ 500 chars)
     * @param nextRetryAt absolute time of the next attempt; ignored on exhaustion
     */
    public void markRetryable(String error, Instant nextRetryAt) {
        ensurePending(DeliveryStatus.PENDING);
        attemptCount++;
        lastError = trim(error);
        if (attemptCount >= maxAttempts) {
            status = DeliveryStatus.FAILED;
            scheduledRetryAt = null;
            throw new DeliveryRetryExhaustedException(id, attemptCount);
        }
        scheduledRetryAt = Objects.requireNonNull(nextRetryAt, "nextRetryAt");
    }

    /**
     * A permanent (do-not-retry) failure. PENDING → FAILED without consuming further
     * retry budget.
     */
    public void markFailedPermanent(String error) {
        ensurePending(DeliveryStatus.FAILED);
        attemptCount++;
        status = DeliveryStatus.FAILED;
        scheduledRetryAt = null;
        lastError = trim(error);
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

    public String id() { return id; }
    public String channel() { return channel; }
    public String recipient() { return recipient; }
    public String title() { return title; }
    public String body() { return body; }
    public String payloadJson() { return payloadJson; }
    public int maxAttempts() { return maxAttempts; }
    public DeliveryStatus status() { return status; }
    public int attemptCount() { return attemptCount; }
    public Optional<Instant> scheduledRetryAt() { return Optional.ofNullable(scheduledRetryAt); }
    public Optional<String> lastError() { return Optional.ofNullable(lastError); }
}
