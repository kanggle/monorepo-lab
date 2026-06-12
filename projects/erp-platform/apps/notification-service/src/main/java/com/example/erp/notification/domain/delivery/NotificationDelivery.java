package com.example.erp.notification.domain.delivery;

import com.example.erp.notification.domain.error.DeliveryStateTransitionInvalidException;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Delivery-lifecycle aggregate (pure domain). Carries the ADR-MONO-005
 * <b>Category C</b> structure ({@code status} + {@code attemptCount} +
 * {@code scheduledRetryAt} + {@code version}). This is a <em>delivery</em>
 * machine, NOT a domain-fact machine — it governs "did this notification reach
 * the recipient", never approval state (architecture.md § Scope discipline).
 *
 * <h2>State machine</h2>
 * <pre>
 *   PENDING ─[markDelivered()]──────────────────────────→ DELIVERED ★ (v1 IN_APP: attempt_count=1, synchronous)
 *   PENDING ─[markRetryable(), attempt < max]───────────→ PENDING  (scheduledRetryAt set)   ← v2 external
 *   PENDING ─[markRetryable() at max OR markFailed()]───→ FAILED ★                          ← v2 external
 * </pre>
 *
 * <p>★ terminal ({@code DELIVERED}/{@code FAILED}) — immutable; any further
 * transition raises {@link DeliveryStateTransitionInvalidException}.
 *
 * <p><b>First increment (IN_APP)</b>: delivery is the persist itself, so the
 * delivery is created and immediately {@link #markDelivered(Instant)} with
 * {@code attemptCount = 1} in the same transaction. The retry loop
 * ({@link #markRetryable}) is the v2 external-channel path and is not exercised
 * in v1.
 */
public final class NotificationDelivery {

    /** Category C retry budget (ADR-MONO-005 § D5 cap 5) — exercised in v2. */
    public static final int DEFAULT_MAX_ATTEMPTS = 5;
    private static final int LAST_ERROR_MAX_LENGTH = 500;

    private final String id;
    private final String tenantId;
    private final String notificationId;
    private final String eventId;
    private final DeliveryChannel channel;
    private final int maxAttempts;
    private final Instant createdAt;

    private DeliveryStatus status;
    private int attemptCount;
    private Instant scheduledRetryAt;
    private String lastError;
    private int version;
    private Instant updatedAt;

    /** Constructor for a fresh PENDING delivery. */
    public static NotificationDelivery createPending(String id,
                                                     String tenantId,
                                                     String notificationId,
                                                     String eventId,
                                                     DeliveryChannel channel,
                                                     Instant now) {
        return new NotificationDelivery(id, tenantId, notificationId, eventId, channel,
                DEFAULT_MAX_ATTEMPTS, DeliveryStatus.PENDING, 0, null, null, 0, now, now);
    }

    /**
     * Constructor for a fresh PENDING external-channel delivery (TASK-ERP-BE-020,
     * v2.0). Unlike the IN_APP {@link #createPending} (delivered synchronously in
     * the consume transaction), an external delivery is created <b>immediately
     * due</b> ({@code scheduledRetryAt = now}) and left PENDING for the
     * {@code DeliveryRetryScheduler} to attempt asynchronously — external I/O must
     * never run in the Kafka consume transaction.
     */
    public static NotificationDelivery createPendingExternal(String id,
                                                             String tenantId,
                                                             String notificationId,
                                                             String eventId,
                                                             DeliveryChannel channel,
                                                             Instant now) {
        return new NotificationDelivery(id, tenantId, notificationId, eventId, channel,
                DEFAULT_MAX_ATTEMPTS, DeliveryStatus.PENDING, 0, now, null, 0, now, now);
    }

    /** Reconstruction constructor (persistence adapter). */
    public NotificationDelivery(String id,
                                String tenantId,
                                String notificationId,
                                String eventId,
                                DeliveryChannel channel,
                                int maxAttempts,
                                DeliveryStatus status,
                                int attemptCount,
                                Instant scheduledRetryAt,
                                String lastError,
                                int version,
                                Instant createdAt,
                                Instant updatedAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.notificationId = Objects.requireNonNull(notificationId, "notificationId");
        this.eventId = Objects.requireNonNull(eventId, "eventId");
        this.channel = Objects.requireNonNull(channel, "channel");
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
            throw new IllegalArgumentException("scheduledRetryAt may only be set while PENDING");
        }
    }

    /**
     * Delivery succeeded. Transitions PENDING → DELIVERED, {@code attemptCount++}
     * (1 on the v1 IN_APP first attempt). Any other source state is rejected.
     */
    public void markDelivered(Instant now) {
        ensurePending(DeliveryStatus.DELIVERED);
        attemptCount++;
        status = DeliveryStatus.DELIVERED;
        scheduledRetryAt = null;
        lastError = null;
        version++;
        updatedAt = now;
    }

    /**
     * Transient external failure (<b>v2 path</b>). Either schedules another
     * retry (PENDING → PENDING with {@code scheduledRetryAt}) or exhausts the
     * budget and fails permanently (PENDING → FAILED at {@code maxAttempts}).
     */
    public void markRetryable(String error, Duration backoff, Instant now) {
        ensurePending(DeliveryStatus.PENDING);
        attemptCount++;
        lastError = trim(error);
        if (attemptCount >= maxAttempts) {
            status = DeliveryStatus.FAILED;
            scheduledRetryAt = null;
            version++;
            updatedAt = now;
            return;
        }
        Objects.requireNonNull(backoff, "backoff");
        scheduledRetryAt = now.plus(backoff);
        version++;
        updatedAt = now;
    }

    /** Permanent external failure (<b>v2 path</b>). Transitions PENDING → FAILED. */
    public void markFailed(String error, Instant now) {
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

    public boolean isTerminal() {
        return status.isTerminal();
    }

    public String id() { return id; }
    public String tenantId() { return tenantId; }
    public String notificationId() { return notificationId; }
    public String eventId() { return eventId; }
    public DeliveryChannel channel() { return channel; }
    public int maxAttempts() { return maxAttempts; }
    public DeliveryStatus status() { return status; }
    public int attemptCount() { return attemptCount; }
    public Optional<Instant> scheduledRetryAt() { return Optional.ofNullable(scheduledRetryAt); }
    public Optional<String> lastError() { return Optional.ofNullable(lastError); }
    public int version() { return version; }
    public Instant createdAt() { return createdAt; }
    public Instant updatedAt() { return updatedAt; }
}
