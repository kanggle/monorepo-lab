package com.example.shipping.application.result;

import com.example.shipping.domain.model.ShippingStatus;

import java.time.Instant;

public record UpdateShippingStatusResult(
        String shippingId,
        ShippingStatus status,
        Instant updatedAt
) {}
