package com.example.shipping.infrastructure.event;

import com.example.messaging.outbox.OutboxRowEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity backing {@code shipping_outbox} (TASK-BE-446, outbox v2).
 *
 * <p>Extends the shared {@link OutboxRowEntity} {@code @MappedSuperclass} from
 * {@code libs/java-messaging} (ADR-MONO-004 § 5). Mirrors the wms
 * {@code MasterOutboxEntity} / ecommerce {@code PromotionOutboxEntity} reference.
 *
 * <p>Lives under {@code com.example.shipping.infrastructure.event} so it is
 * picked up by the application's {@code @EntityScan(basePackages = "com.example.shipping")}.
 * (TASK-MONO-406 dropped {@code "com.example.messaging"} from that list: the library holds no
 * {@code @Entity}, and the {@code @MappedSuperclass} above is resolved via the entity class
 * hierarchy, not via entity scanning.)
 */
@Entity
@Table(name = "shipping_outbox")
public class ShippingOutboxEntity extends OutboxRowEntity {

    protected ShippingOutboxEntity() {
        super();
    }

    public ShippingOutboxEntity(UUID eventId, String eventType, String aggregateType,
                                String aggregateId, String partitionKey, String payload,
                                Instant occurredAt) {
        super(eventId, eventType, aggregateType, aggregateId, partitionKey, payload, occurredAt);
    }
}
