package com.wms.outbound.adapter.in.web.dto.response;

import com.wms.outbound.application.result.OrderSummaryResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Wire shape of one row in the paginated list response, per
 * {@code outbound-service-api.md} §1.3.
 */
public record OrderSummaryResponse(
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

    public static OrderSummaryResponse from(OrderSummaryResult r) {
        return new OrderSummaryResponse(
                r.orderId(),
                r.orderNo(),
                r.source(),
                r.customerPartnerId(),
                r.warehouseId(),
                r.status(),
                r.sagaState(),
                r.lineCount(),
                r.totalQtyOrdered(),
                r.requiredShipDate(),
                r.createdAt(),
                r.updatedAt());
    }
}
