package com.wms.outbound.application.result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Slim row used for the paginated list endpoint. No {@code lines} array —
 * fetch detail via {@code GET /orders/{id}}.
 */
public record OrderSummaryResult(
        UUID orderId,
        String orderNo,
        String source,
        UUID customerPartnerId,
        UUID warehouseId,
        String status,
        String sagaState,
        int lineCount,
        long totalQtyOrdered,
        LocalDate requiredShipDate,
        Instant createdAt,
        Instant updatedAt
) {
}
