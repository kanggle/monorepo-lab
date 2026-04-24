package com.example.shipping.interfaces.rest.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateShippingStatusRequest(
        @NotNull(message = "status must not be null")
        String status,
        String trackingNumber,
        String carrier
) {}
