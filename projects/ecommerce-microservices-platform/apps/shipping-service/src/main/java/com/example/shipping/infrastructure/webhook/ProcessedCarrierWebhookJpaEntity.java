package com.example.shipping.infrastructure.webhook;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Persistent idempotency marker for a processed carrier webhook delivery (TASK-BE-294).
 * Backed by a dedicated {@code processed_carrier_webhooks} table — kept separate from
 * {@code processed_events} (Kafka event dedup) so the two have independent retention.
 */
@Entity
@Table(name = "processed_carrier_webhooks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedCarrierWebhookJpaEntity {

    @Id
    @Column(name = "delivery_id", nullable = false, updatable = false)
    private String deliveryId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    static ProcessedCarrierWebhookJpaEntity of(String deliveryId, Instant receivedAt) {
        ProcessedCarrierWebhookJpaEntity entity = new ProcessedCarrierWebhookJpaEntity();
        entity.deliveryId = deliveryId;
        entity.receivedAt = receivedAt;
        return entity;
    }
}
