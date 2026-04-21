package com.example.product.domain.event;

public record ProductDeletedPayload(
        String productId
) implements EventPayload {}
