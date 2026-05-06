package com.example.scmplatform.procurement.presentation.dto;

import com.example.scmplatform.procurement.application.PurchaseOrderView;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record PurchaseOrderResponse(
        String id,
        String tenantId,
        String poNumber,
        String supplierId,
        String buyerAccountId,
        PoStatus status,
        BigDecimal totalAmount,
        String currency,
        Instant submittedAt,
        Instant acknowledgedAt,
        Instant confirmedAt,
        Instant canceledAt,
        Instant createdAt,
        Instant updatedAt,
        List<LineResponse> lines
) {

    public record LineResponse(
            String id,
            int lineNo,
            String sku,
            String supplierSku,
            BigDecimal quantity,
            BigDecimal unitPrice,
            BigDecimal receivedQuantity
    ) {
    }

    public static PurchaseOrderResponse from(PurchaseOrderView v) {
        List<LineResponse> lines = v.lines().stream()
                .map(l -> new LineResponse(l.id(), l.lineNo(), l.sku(), l.supplierSku(),
                        l.quantity(), l.unitPrice(), l.receivedQuantity()))
                .toList();
        return new PurchaseOrderResponse(
                v.id(), v.tenantId(), v.poNumber(), v.supplierId(), v.buyerAccountId(),
                v.status(), v.totalAmount(), v.currency(),
                v.submittedAt(), v.acknowledgedAt(), v.confirmedAt(), v.canceledAt(),
                v.createdAt(), v.updatedAt(),
                lines
        );
    }
}
