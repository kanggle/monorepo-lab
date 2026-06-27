package com.example.promotion.infrastructure.event;

import com.example.messaging.outbox.OutboxRowEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity backing {@code promotion_outbox} (TASK-BE-444, outbox v2).
 *
 * <p>Extends the shared {@link OutboxRowEntity} {@code @MappedSuperclass} from
 * {@code libs/java-messaging} (ADR-MONO-004 § 5), so the generic
 * {@code AbstractOutboxPublisher} drives this table through the
 * {@link com.example.messaging.outbox.OutboxRow} contract. All column mappings
 * (UUID {@code event_id} PK, {@code occurred_at}, {@code retries},
 * {@code last_error}, …) are inherited from the superclass; this class only
 * binds the table name and exposes constructors. Mirrors the wms
 * {@code MasterOutboxEntity} (the CI-validated Postgres v2 reference).
 *
 * <p>Lives under {@code com.example.promotion.infrastructure.event} so it is
 * picked up by the application's {@code @EntityScan(basePackages =
 * "com.example.promotion")}.
 */
@Entity
@Table(name = "promotion_outbox")
public class PromotionOutboxEntity extends OutboxRowEntity {

    protected PromotionOutboxEntity() {
        super();
    }

    public PromotionOutboxEntity(UUID eventId, String eventType, String aggregateType,
                                 String aggregateId, String partitionKey, String payload,
                                 Instant occurredAt) {
        super(eventId, eventType, aggregateType, aggregateId, partitionKey, payload, occurredAt);
    }
}
