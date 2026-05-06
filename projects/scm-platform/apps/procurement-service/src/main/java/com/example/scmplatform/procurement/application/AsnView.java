package com.example.scmplatform.procurement.application;

import com.example.scmplatform.procurement.domain.asn.AdvanceShipmentNotice;
import com.example.scmplatform.procurement.domain.asn.AsnLine;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record AsnView(
        String id,
        String poId,
        String tenantId,
        String supplierAsnRef,
        Instant expectedArrivalAt,
        Instant receivedAt,
        List<LineView> lines
) {

    public record LineView(String id, String poLineId, BigDecimal quantityShipped, BigDecimal quantityReceived) {
    }

    public static AsnView from(AdvanceShipmentNotice asn) {
        List<LineView> lines = asn.linesView().stream()
                .map(AsnView::lineView)
                .toList();
        return new AsnView(
                asn.getId(),
                asn.getPoId(),
                asn.getTenantId(),
                asn.getSupplierAsnRef(),
                asn.getExpectedArrivalAt(),
                asn.getReceivedAt(),
                lines
        );
    }

    private static LineView lineView(AsnLine l) {
        return new LineView(l.getId(), l.getPoLineId(), l.getQuantityShipped(), l.getQuantityReceived());
    }
}
