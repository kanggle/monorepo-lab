package com.example.scmplatform.procurement.infrastructure.persistence.jpa;

import com.example.messaging.outbox.OutboxRowEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity backing {@code procurement_outbox} (TASK-SCM-BE-032, outbox v2).
 *
 * <p>Extends the shared {@link OutboxRowEntity} {@code @MappedSuperclass} from
 * {@code libs/java-messaging} (ADR-MONO-004 § 5), so the generic
 * {@code AbstractOutboxPublisher} drives this table through the
 * {@link com.example.messaging.outbox.OutboxRow} contract. All column mappings
 * (UUID {@code event_id} PK, {@code occurred_at}, {@code retries},
 * {@code last_error}, …) are inherited from the superclass; this class only
 * binds the table name and exposes constructors. Mirrors the wms
 * {@code MasterOutboxEntity} / ecommerce {@code PromotionOutboxEntity} (the
 * CI-validated Postgres v2 reference).
 *
 * <p>Lives under {@code com.example.scmplatform.procurement.infrastructure.persistence.jpa}
 * so it is picked up by the application's
 * {@code @EntityScan(basePackages = {"…domain", "…infrastructure.persistence.jpa"})}
 * and the repository by its
 * {@code @EnableJpaRepositories(basePackages = "…infrastructure.persistence.jpa")}.
 */
@Entity
@Table(name = "procurement_outbox")
public class ProcurementOutboxJpaEntity extends OutboxRowEntity {

    protected ProcurementOutboxJpaEntity() {
        super();
    }

    public ProcurementOutboxJpaEntity(UUID eventId, String eventType, String aggregateType,
                                      String aggregateId, String partitionKey, String payload,
                                      Instant occurredAt) {
        super(eventId, eventType, aggregateType, aggregateId, partitionKey, payload, occurredAt);
    }
}
