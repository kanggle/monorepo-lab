package com.example.scmplatform.procurement.presentation.dto;

import com.example.scmplatform.procurement.application.AsnView;
import com.fasterxml.jackson.annotation.JsonFormat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AsnResponse(
        String id,
        String poId,
        String tenantId,
        String supplierAsnRef,
        Instant expectedArrivalAt,
        Instant receivedAt,
        List<LineResponse> lines
) {

    public record LineResponse(
            String id,
            String poLineId,
            // Jackson's default BigDecimal serialisation is a number; @JsonFormat
            // pins these to JSON strings per procurement-api.md (e.g. "5.0000"),
            // matching PurchaseOrderResponse's decimal contract (TASK-SCM-BE-020).
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal quantityShipped,
            @JsonFormat(shape = JsonFormat.Shape.STRING)
            BigDecimal quantityReceived
    ) {
    }

    public static AsnResponse from(AsnView v) {
        List<LineResponse> lines = v.lines().stream()
                .map(l -> new LineResponse(l.id(), l.poLineId(), l.quantityShipped(), l.quantityReceived()))
                .toList();
        return new AsnResponse(v.id(), v.poId(), v.tenantId(), v.supplierAsnRef(),
                v.expectedArrivalAt(), v.receivedAt(), lines);
    }
}
