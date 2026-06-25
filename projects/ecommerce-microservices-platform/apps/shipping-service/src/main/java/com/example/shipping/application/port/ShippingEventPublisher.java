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

    /**
     * Writes an outbox row for the operator-driven manual ship-confirm leg
     * (ADR-MONO-022 D4 v2(c), {@code ecommerce.shipping.manual-confirm-requested.v1}).
     * Emitted only when an operator marks a Shipping {@code SHIPPED} with
     * {@code deductWmsInventory=true} on a {@code wmsRouted} order, so the wms
     * outbound-service confirms the shipment and deducts physical inventory. The
     * envelope is the wms camelCase shape (same ACL convention as the forward leg).
     *
     * @param tenantId       the shipping row tenant (envelope top-level).
     * @param orderId        ecommerce orderId == wms orderNo (D5 correlation key).
     * @param carrier        operator-entered carrier code, nullable (wms default if blank).
     * @param trackingNumber operator-entered tracking number, nullable (informational on wms).
     */
    void publishManualShipConfirmRequested(String tenantId, String orderId,
                                           String carrier, String trackingNumber);
}
