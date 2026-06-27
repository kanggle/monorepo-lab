package com.example.order.infrastructure.persistence;

import com.example.messaging.outbox.OutboxRowEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity backing {@code order_outbox} (TASK-BE-448, outbox v2).
 *
 * <p>Extends the shared {@link OutboxRowEntity} {@code @MappedSuperclass} from
 * {@code libs/java-messaging} (ADR-MONO-004 § 5). Mirrors the wms
 * {@code MasterOutboxEntity} / ecommerce {@code PromotionOutboxEntity} reference.
 *
 * <p>Lives under {@code com.example.order.infrastructure.persistence} so it is
 * picked up by both {@code @EntityScan(basePackages = {"com.example.order",
 * "com.example.messaging"})} and
 * {@code @EnableJpaRepositories(basePackages = "com.example.order.infrastructure.persistence")}.
 */
@Entity
@Table(name = "order_outbox")
public class OrderOutboxEntity extends OutboxRowEntity {

    protected OrderOutboxEntity() {
        super();
    }

    public OrderOutboxEntity(UUID eventId, String eventType, String aggregateType,
                             String aggregateId, String partitionKey, String payload,
                             Instant occurredAt) {
        super(eventId, eventType, aggregateType, aggregateId, partitionKey, payload, occurredAt);
    }
}
