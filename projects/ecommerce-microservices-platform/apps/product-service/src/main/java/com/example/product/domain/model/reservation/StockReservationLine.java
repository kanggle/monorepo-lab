package com.example.product.domain.model.reservation;

import java.util.UUID;

/**
 * One line of a {@link StockReservation} — the quantity of a single variant the paid order
 * needs reserved. Captured immutably from the {@code OrderPlaced} line snapshot
 * ({@code productId}/{@code variantId}/{@code quantity}); product-service never calls back to
 * order-service for line data (TASK-BE-428).
 */
public record StockReservationLine(UUID variantId, UUID productId, int quantity) {

    public StockReservationLine {
        if (variantId == null) {
            throw new IllegalArgumentException("variantId must not be null");
        }
        if (productId == null) {
            throw new IllegalArgumentException("productId must not be null");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("reservation line quantity must be positive: " + quantity);
        }
    }
}
