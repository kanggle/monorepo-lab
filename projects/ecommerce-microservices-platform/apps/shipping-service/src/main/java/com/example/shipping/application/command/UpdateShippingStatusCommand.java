package com.example.shipping.application.command;

import com.example.shipping.domain.model.ShippingStatus;

public record UpdateShippingStatusCommand(
        String shippingId,
        ShippingStatus status,
        String trackingNumber,
        String carrier,
        boolean deductWmsInventory,
        String userRole
) {}
