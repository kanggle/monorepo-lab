package com.example.scmplatform.procurement.presentation.dto;

import com.example.scmplatform.procurement.application.AsnView;

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

    public record LineResponse(String id, String poLineId, BigDecimal quantityShipped, BigDecimal quantityReceived) {
    }

    public static AsnResponse from(AsnView v) {
        List<LineResponse> lines = v.lines().stream()
                .map(l -> new LineResponse(l.id(), l.poLineId(), l.quantityShipped(), l.quantityReceived()))
                .toList();
        return new AsnResponse(v.id(), v.poId(), v.tenantId(), v.supplierAsnRef(),
                v.expectedArrivalAt(), v.receivedAt(), lines);
    }
}
