package com.example.review.infrastructure.event;

import com.example.messaging.outbox.OutboxRowEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity backing {@code review_outbox} (TASK-BE-445, outbox v2).
 *
 * <p>Extends the shared {@link OutboxRowEntity} {@code @MappedSuperclass} from
 * {@code libs/java-messaging} (ADR-MONO-004 § 5), so the generic
 * {@code AbstractOutboxPublisher} drives this table through the
 * {@link com.example.messaging.outbox.OutboxRow} contract. Mirrors the wms
 * {@code MasterOutboxEntity} / ecommerce {@code PromotionOutboxEntity} reference.
 *
 * <p>Lives under {@code com.example.review.infrastructure.event} so it is picked
 * up by the application's {@code @EntityScan(basePackages = "com.example.review")}.
 */
@Entity
@Table(name = "review_outbox")
public class ReviewOutboxEntity extends OutboxRowEntity {

    protected ReviewOutboxEntity() {
        super();
    }

    public ReviewOutboxEntity(UUID eventId, String eventType, String aggregateType,
                              String aggregateId, String partitionKey, String payload,
                              Instant occurredAt) {
        super(eventId, eventType, aggregateType, aggregateId, partitionKey, payload, occurredAt);
    }
}
