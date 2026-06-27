package com.example.settlement.infrastructure.persistence;

import com.example.messaging.outbox.OutboxRowEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity backing {@code settlement_outbox} (TASK-BE-447, outbox v2).
 *
 * <p>Extends the shared {@link OutboxRowEntity} {@code @MappedSuperclass} from
 * {@code libs/java-messaging} (ADR-MONO-004 § 5). Mirrors the wms
 * {@code MasterOutboxEntity} / ecommerce {@code PromotionOutboxEntity} reference.
 *
 * <p>Lives under {@code com.example.settlement.infrastructure.persistence} so it is
 * picked up by the application's {@code @EntityScan(basePackages =
 * {"com.example.settlement", "com.example.messaging"})}.
 */
@Entity
@Table(name = "settlement_outbox")
public class SettlementOutboxEntity extends OutboxRowEntity {

    protected SettlementOutboxEntity() {
        super();
    }

    public SettlementOutboxEntity(UUID eventId, String eventType, String aggregateType,
                                  String aggregateId, String partitionKey, String payload,
                                  Instant occurredAt) {
        super(eventId, eventType, aggregateType, aggregateId, partitionKey, payload, occurredAt);
    }
}
