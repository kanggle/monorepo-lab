package com.example.product.domain.event;

public record StockChangedPayload(
        String productId,
        String variantId,
        int previousStock,
        int currentStock,
        int delta,
        String reason,
        String orderId
) implements EventPayload {}
