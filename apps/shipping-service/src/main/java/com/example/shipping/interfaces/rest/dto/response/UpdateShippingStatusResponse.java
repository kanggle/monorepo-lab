package com.example.shipping.interfaces.rest.dto.response;

import com.example.shipping.application.result.UpdateShippingStatusResult;

public record UpdateShippingStatusResponse(
        String shippingId,
        String status,
        String updatedAt
) {
    public static UpdateShippingStatusResponse from(UpdateShippingStatusResult result) {
        return new UpdateShippingStatusResponse(
                result.shippingId(),
                result.status().name(),
                result.updatedAt().toString()
        );
    }
}
