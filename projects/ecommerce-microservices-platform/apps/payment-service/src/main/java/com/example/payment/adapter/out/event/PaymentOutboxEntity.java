package com.example.payment.adapter.out.event;

import com.example.messaging.outbox.OutboxRowEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity backing {@code payment_outbox} (TASK-BE-449, outbox v2).
 *
 * <p>Extends the shared {@link OutboxRowEntity} {@code @MappedSuperclass} from
 * {@code libs/java-messaging} (ADR-MONO-004 § 5). Mirrors the wms
 * {@code MasterOutboxEntity} / ecommerce {@code PromotionOutboxEntity} reference.
 *
 * <p>Lives under {@code com.example.payment.adapter.out.event} so it is picked up
 * by the application's default component/entity scan ({@code @SpringBootApplication}
 * on {@code com.example.payment}).
 */
@Entity
@Table(name = "payment_outbox")
public class PaymentOutboxEntity extends OutboxRowEntity {

    protected PaymentOutboxEntity() {
        super();
    }

    public PaymentOutboxEntity(UUID eventId, String eventType, String aggregateType,
                               String aggregateId, String partitionKey, String payload,
                               Instant occurredAt) {
        super(eventId, eventType, aggregateType, aggregateId, partitionKey, payload, occurredAt);
    }
}
