package com.wms.outbound.adapter.in.web.dto.response;

import com.wms.outbound.application.result.OrderResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Wire shape of the {@code GET /orders/{id}} and {@code POST /orders}
 * responses, per {@code outbound-service-api.md} §1.1.
 */
public record OrderResponse(
        UUID orderId,
        String orderNo,
        String source,
        UUID customerPartnerId,
        UUID warehouseId,
        LocalDate requiredShipDate,
        String notes,
        String status,
        List<OrderLineResponse> lines,
        UUID sagaId,
        String sagaState,
        long version,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy
) {

    public static OrderResponse from(OrderResult r) {
        return new OrderResponse(
                r.orderId(),
                r.orderNo(),
                r.source(),
                r.customerPartnerId(),
                r.warehouseId(),
                r.requiredShipDate(),
                r.notes(),
                r.status(),
                r.lines().stream().map(OrderLineResponse::from).toList(),
                r.sagaId(),
                r.sagaState(),
                r.version(),
                r.createdAt(),
                r.createdBy(),
                r.updatedAt(),
                r.updatedBy());
    }
}
