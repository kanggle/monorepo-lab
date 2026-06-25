package com.example.shipping.interfaces.rest.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateShippingStatusRequest(
        @NotNull(message = "status must not be null")
        String status,
        String trackingNumber,
        String carrier,
        /**
         * Optional (default false). When true AND the order is {@code wmsRouted} AND the
         * target status is {@code SHIPPED}, the operator's manual ship-confirm also
         * publishes {@code ecommerce.shipping.manual-confirm-requested.v1} so wms deducts
         * physical inventory (ADR-MONO-022 D4 v2(c)). No-op otherwise.
         */
        Boolean deductWmsInventory
) {}
