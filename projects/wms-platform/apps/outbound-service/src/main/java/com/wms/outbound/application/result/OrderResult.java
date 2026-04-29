package com.wms.outbound.application.result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Output record for {@code ReceiveOrderUseCase}, {@code CancelOrderUseCase},
 * and {@code QueryOrderUseCase.findById}. Mirrors the {@code OrderResponse}
 * shape declared in {@code outbound-service-api.md} §1.1.
 */
public record OrderResult(
        UUID orderId,
        String orderNo,
        String source,
        UUID customerPartnerId,
        UUID warehouseId,
        LocalDate requiredShipDate,
        String notes,
        String status,
        long version,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy,
        List<OrderLineResult> lines,
        UUID sagaId,
        String sagaState
) {
}
