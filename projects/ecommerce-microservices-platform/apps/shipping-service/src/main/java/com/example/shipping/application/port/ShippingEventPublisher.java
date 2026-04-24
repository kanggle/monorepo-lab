package com.example.shipping.application.port;

import com.example.shipping.domain.model.ShippingStatus;

public interface ShippingEventPublisher {

    void publishShippingStatusChanged(String shippingId, String orderId, String userId,
                                       ShippingStatus previousStatus, ShippingStatus newStatus,
                                       String trackingNumber, String carrier);
}
