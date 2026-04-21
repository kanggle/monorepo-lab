package com.example.product.domain.event;

public sealed interface EventPayload permits ProductCreatedPayload, ProductUpdatedPayload, ProductDeletedPayload, StockChangedPayload, ProductImagesUpdatedPayload {
}
