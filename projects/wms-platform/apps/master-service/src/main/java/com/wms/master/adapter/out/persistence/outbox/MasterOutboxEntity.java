package com.wms.master.adapter.out.persistence.outbox;

import com.example.messaging.outbox.OutboxRowEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity backing {@code master_outbox} (TASK-BE-438, outbox v2).
 *
 * <p>Extends the shared {@link OutboxRowEntity} {@code @MappedSuperclass} from
 * {@code libs/java-messaging} (ADR-MONO-004 § 5), so the generic
 * {@code AbstractOutboxPublisher} drives this table through the
 * {@link com.example.messaging.outbox.OutboxRow} contract. All column mappings
 * (UUID {@code event_id} PK, {@code occurred_at}, {@code retries},
 * {@code last_error}, …) are inherited from the superclass; this class only
 * binds the table name and exposes constructors.
 *
 * <p>Lives under {@code com.wms.master.adapter.out.persistence} so it is picked
 * up by {@code MasterServicePersistenceConfig}'s {@code @EntityScan}.
 */
@Entity
@Table(name = "master_outbox")
public class MasterOutboxEntity extends OutboxRowEntity {

    protected MasterOutboxEntity() {
        super();
    }

    public MasterOutboxEntity(UUID eventId, String eventType, String aggregateType,
                              String aggregateId, String partitionKey, String payload,
                              Instant occurredAt) {
        super(eventId, eventType, aggregateType, aggregateId, partitionKey, payload, occurredAt);
    }
}
