package com.example.shipping.application.result;

import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.model.ShippingStatus;

import java.time.Instant;

public record ShippingSummary(
        String shippingId,
        String orderId,
        ShippingStatus status,
        String trackingNumber,
        String carrier,
        Instant createdAt,
        Instant updatedAt
) {
    public static ShippingSummary from(Shipping shipping) {
        return new ShippingSummary(
                shipping.getShippingId(),
                shipping.getOrderId(),
                shipping.getStatus(),
                shipping.getTrackingNumber(),
                shipping.getCarrier(),
                shipping.getCreatedAt(),
                shipping.getUpdatedAt()
        );
    }
}
