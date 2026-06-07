package com.example.shipping.application.port;

import com.example.shipping.domain.model.ShippingStatus;

public interface ShippingEventPublisher {

    void publishShippingStatusChanged(String shippingId, String orderId, String userId,
                                       ShippingStatus previousStatus, ShippingStatus newStatus,
                                       String trackingNumber, String carrier);

    /**
     * Writes an outbox row for the cross-project fulfillment forward leg
     * (ADR-MONO-022 §D7). {@code messageJson} is the already-serialized wms
     * camelCase envelope (ACL output); {@code orderId} is the outbox aggregateId.
     */
    void publishFulfillmentRequested(String orderId, String messageJson);
}
