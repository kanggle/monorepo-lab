package com.example.product.application.command;

import java.util.UUID;

/**
 * {@code idempotencyKey} is required by {@code AdjustStockService.adjust}
 * (TASK-BE-536) — a replayed request must not adjust stock a second time. Carried
 * on the command (not a bare method parameter) to mirror this record's existing
 * fields; every caller must supply one.
 */
public record AdjustStockCommand(UUID productId, UUID variantId, int quantity, String reason,
                                 String idempotencyKey) {}
