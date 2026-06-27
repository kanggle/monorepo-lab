package com.example.platform.notification.delivery.jpa;

import com.example.platform.notification.delivery.DeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.Version;

import java.time.Instant;

/**
 * Optional JPA {@link MappedSuperclass} carrying the channel-agnostic delivery
 * lifecycle columns, so a service's concrete delivery entity can extend it and add
 * its own domain columns (event id, source topic, idempotency key, payload column
 * type, table name). Mirrors how {@code libs/java-messaging} provides
 * {@link com.example.messaging.outbox.OutboxRowEntity}.
 *
 * <p>Lifted from the wms reference {@code NotificationDeliveryJpaEntity}, keeping
 * only the shared lifecycle fields and the {@link Version} optimistic lock. It is
 * <b>DB-portable</b> — no Postgres-only column types here (the service decides how to
 * map its payload column, e.g. JSONB vs TEXT). Not annotated {@code @Entity} so it is
 * not scanned on its own.
 *
 * <p>Columns:
 * <pre>
 *   status             VARCHAR(16) NOT NULL,
 *   attempt_count      INT         NOT NULL,
 *   scheduled_retry_at TIMESTAMP,
 *   last_error         VARCHAR(500),
 *   version            INT         NOT NULL   (@Version optimistic lock)
 * </pre>
 *
 * <p>Subclasses add {@code @Entity @Table(name = "...")} + their own id and domain
 * columns, and map to/from {@link com.example.platform.notification.delivery.DeliveryRecord}
 * in the service's persistence adapter.
 */
@MappedSuperclass
public abstract class DeliveryRecordEntity {

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    protected DeliveryStatus status;

    @Column(name = "attempt_count", nullable = false)
    protected int attemptCount;

    @Column(name = "scheduled_retry_at")
    protected Instant scheduledRetryAt;

    @Column(name = "last_error", length = 500)
    protected String lastError;

    @Version
    @Column(name = "version", nullable = false)
    protected int version;

    protected DeliveryRecordEntity() {
    }

    public DeliveryStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getScheduledRetryAt() { return scheduledRetryAt; }
    public String getLastError() { return lastError; }
    public int getVersion() { return version; }

    /**
     * Apply the channel-agnostic lifecycle state from a transitioned
     * {@link com.example.platform.notification.delivery.DeliveryRecord}. The subclass
     * (or the service adapter) is responsible for the domain columns.
     */
    public void applyLifecycle(DeliveryStatus status,
                               int attemptCount,
                               Instant scheduledRetryAt,
                               String lastError) {
        this.status = status;
        this.attemptCount = attemptCount;
        this.scheduledRetryAt = scheduledRetryAt;
        this.lastError = lastError;
    }
}
