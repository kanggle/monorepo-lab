package com.wms.notification.adapter.outbound.persistence.jpa.delivery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA entity for {@code notification_delivery}. {@code payload_snapshot} is
 * JSONB — annotated with {@link JdbcTypeCode SqlTypes.JSON} per the
 * regression-guard learning (TASK-SCM-INT-001b root cause #2 /
 * TASK-SCM-BE-005).
 *
 * <p>{@link Version} maps to the SQL {@code version} column for JPA
 * optimistic locking (architecture.md § Concurrency Control).
 */
@Entity
@Table(name = "notification_delivery")
public class NotificationDeliveryJpaEntity {

    @Id
    private UUID id;

    @Column(name = "event_id", nullable = false)
    private UUID eventId;

    @Column(name = "source_topic", nullable = false, length = 120)
    private String sourceTopic;

    @Column(name = "channel_id", nullable = false, length = 120)
    private String channelId;

    @Column(nullable = false, length = 255)
    private String recipient;

    @Column(name = "delivery_idempotency_key", nullable = false, length = 64, unique = true)
    private String deliveryIdempotencyKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload_snapshot", nullable = false, columnDefinition = "jsonb")
    private String payloadSnapshot;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "scheduled_retry_at")
    private Instant scheduledRetryAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected NotificationDeliveryJpaEntity() {
    }

    public NotificationDeliveryJpaEntity(UUID id, UUID eventId, String sourceTopic, String channelId,
                                         String recipient, String deliveryIdempotencyKey,
                                         String payloadSnapshot, String status, int attemptCount,
                                         Instant scheduledRetryAt, String lastError, int version,
                                         Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.eventId = eventId;
        this.sourceTopic = sourceTopic;
        this.channelId = channelId;
        this.recipient = recipient;
        this.deliveryIdempotencyKey = deliveryIdempotencyKey;
        this.payloadSnapshot = payloadSnapshot;
        this.status = status;
        this.attemptCount = attemptCount;
        this.scheduledRetryAt = scheduledRetryAt;
        this.lastError = lastError;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public UUID getEventId() { return eventId; }
    public String getSourceTopic() { return sourceTopic; }
    public String getChannelId() { return channelId; }
    public String getRecipient() { return recipient; }
    public String getDeliveryIdempotencyKey() { return deliveryIdempotencyKey; }
    public String getPayloadSnapshot() { return payloadSnapshot; }
    public String getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getScheduledRetryAt() { return scheduledRetryAt; }
    public String getLastError() { return lastError; }
    public int getVersion() { return version; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    /** Mutators used by the persistence adapter when applying domain state changes. */
    public void apply(String status, int attemptCount, Instant scheduledRetryAt, String lastError, Instant updatedAt) {
        this.status = status;
        this.attemptCount = attemptCount;
        this.scheduledRetryAt = scheduledRetryAt;
        this.lastError = lastError;
        this.updatedAt = updatedAt;
    }
}
