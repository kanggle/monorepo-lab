package com.example.shipping.application.result;

import com.example.shipping.domain.model.Shipping;
import com.example.shipping.domain.model.ShippingStatus;
import com.example.shipping.domain.model.StatusHistoryEntry;

import java.time.Instant;
import java.util.List;

public record ShippingResult(
        String shippingId,
        String orderId,
        ShippingStatus status,
        String trackingNumber,
        String carrier,
        List<StatusHistoryEntryResult> statusHistory,
        Instant createdAt,
        Instant updatedAt
) {
    public static ShippingResult from(Shipping shipping) {
        List<StatusHistoryEntryResult> history = shipping.getStatusHistory().stream()
                .map(e -> new StatusHistoryEntryResult(e.status(), e.changedAt()))
                .toList();
        return new ShippingResult(
                shipping.getShippingId(),
                shipping.getOrderId(),
                shipping.getStatus(),
                shipping.getTrackingNumber(),
                shipping.getCarrier(),
                history,
                shipping.getCreatedAt(),
                shipping.getUpdatedAt()
        );
    }

    public record StatusHistoryEntryResult(
            ShippingStatus status,
            Instant changedAt
    ) {}
}
