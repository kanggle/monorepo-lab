package com.wms.admin.api.dashboard.dto;

import com.wms.admin.readmodel.outbound.OrderSummaryEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record OrderSummaryResponse(
        UUID orderId,
        String orderNo,
        UUID warehouseId,
        UUID customerPartnerId,
        String customerName,
        String status,
        String source,
        LocalDate requiredShipDate,
        int lineCount,
        String sagaState,
        Instant receivedAt,
        Instant shippedAt,
        Instant lastEventAt,
        long version) {

    public static OrderSummaryResponse from(OrderSummaryEntity e) {
        return new OrderSummaryResponse(
                e.getOrderId(), e.getOrderNo(), e.getWarehouseId(),
                e.getCustomerPartnerId(), e.getCustomerName(),
                e.getStatus(), e.getSource(), e.getRequiredShipDate(),
                e.getLineCount(), e.getSagaState(),
                e.getReceivedAt(), e.getShippedAt(),
                e.getLastEventAt(), e.getVersion());
    }
}
