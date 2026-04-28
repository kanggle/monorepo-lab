package com.wms.inventory.application.command;

import java.util.List;
import java.util.UUID;

/**
 * Reserve stock for a picking request. Lines reference Inventory rows by id;
 * the use-case loads each row and applies {@code Inventory.reserve(qty,
 * reservationId)} atomically.
 *
 * <p>Origins: REST {@code POST /reservations} and the
 * {@code PickingRequestedConsumer}.
 */
public record ReserveStockCommand(
        UUID pickingRequestId,
        UUID warehouseId,
        List<Line> lines,
        int ttlSeconds,
        UUID sourceEventId,
        String actorId,
        String idempotencyKey
) {

    public ReserveStockCommand {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("lines must not be empty");
        }
        lines = List.copyOf(lines);
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("ttlSeconds must be > 0, got: " + ttlSeconds);
        }
    }

    public record Line(UUID inventoryId, int quantity) {
        public Line {
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity must be > 0");
            }
        }
    }
}
