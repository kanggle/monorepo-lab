package com.example.shipping.interfaces.rest.dto.response;

import com.example.shipping.application.result.ShippingResult;

import java.util.List;

public record ShippingResponse(
        String shippingId,
        String orderId,
        String status,
        String trackingNumber,
        String carrier,
        List<StatusHistoryResponse> statusHistory,
        String createdAt,
        String updatedAt
) {
    public static ShippingResponse from(ShippingResult result) {
        List<StatusHistoryResponse> history = result.statusHistory().stream()
                .map(e -> new StatusHistoryResponse(e.status().name(), e.changedAt().toString()))
                .toList();
        return new ShippingResponse(
                result.shippingId(),
                result.orderId(),
                result.status().name(),
                result.trackingNumber(),
                result.carrier(),
                history,
                result.createdAt().toString(),
                result.updatedAt().toString()
        );
    }

    public record StatusHistoryResponse(
            String status,
            String changedAt
    ) {}
}
