package com.example.scmplatform.procurement.presentation.dto;

import com.example.scmplatform.procurement.application.PurchaseOrderView;
import com.example.scmplatform.procurement.domain.po.PoOrigin;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

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
        // Provenance (ADR-MONO-027 D5): origin is always present; sourceSuggestionId
        // is non-null only for demand-planning-materialized POs.
        PoOrigin origin,
        String sourceSuggestionId,
        // procurement-api.md serialises monetary/quantity decimals as STRINGS
        // (e.g. "125000.00") to preserve scale/precision — NOT JSON numbers.
        // Jackson's default BigDecimal serialisation is a number; @JsonFormat
        // STRING brings the wire shape into contract conformance (the
        // platform-console PC-FE-008 consumer parses these as z.string()).
        // (TASK-SCM-BE-020.)
        @JsonFormat(shape = JsonFormat.Shape.STRING)
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
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal quantity,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal unitPrice,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
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
                v.status(), v.origin(), v.sourceSuggestionId(), v.totalAmount(), v.currency(),
                v.submittedAt(), v.acknowledgedAt(), v.confirmedAt(), v.canceledAt(),
                v.createdAt(), v.updatedAt(),
                lines
        );
    }
}
