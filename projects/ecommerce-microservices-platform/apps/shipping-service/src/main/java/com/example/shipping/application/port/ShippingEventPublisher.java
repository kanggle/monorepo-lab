package com.example.shipping.application.port;

import com.example.shipping.domain.model.ShippingStatus;

public interface ShippingEventPublisher {

    /**
     * Publishes a {@code ShippingStatusChanged} outbox event (M5). {@code tenantId} is
     * sourced from the shipping row ({@code saved.getTenantId()}) and stamped on the
     * envelope top-level beside {@code event_id}/{@code source}, so downstream consumers
     * bind the originating tenant. Captured at outbox-write time from the row — no
     * AFTER_COMMIT ThreadLocal-clear race (outbox, not direct in-thread Kafka).
     */
    void publishShippingStatusChanged(String tenantId, String shippingId, String orderId, String userId,
                                       ShippingStatus previousStatus, ShippingStatus newStatus,
                                       String trackingNumber, String carrier);

    /**
     * Writes an outbox row for the cross-project fulfillment forward leg
     * (ADR-MONO-022 §D7). {@code messageJson} is the already-serialized wms
     * camelCase envelope (ACL output, now carrying {@code tenant_id} — M5);
     * {@code orderId} is the outbox aggregateId.
     */
    void publishFulfillmentRequested(String orderId, String messageJson);
}
