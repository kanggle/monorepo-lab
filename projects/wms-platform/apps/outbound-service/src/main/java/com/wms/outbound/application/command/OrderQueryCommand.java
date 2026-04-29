package com.wms.outbound.application.command;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Filter inputs for the paginated list endpoint
 * {@code GET /api/v1/outbound/orders}. All fields are optional.
 */
public record OrderQueryCommand(
        String status,
        UUID warehouseId,
        UUID customerPartnerId,
        String source,
        String orderNo,
        LocalDate requiredShipAfter,
        LocalDate requiredShipBefore,
        Instant createdAfter,
        Instant createdBefore,
        int page,
        int size
) {
}
