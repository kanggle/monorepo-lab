package com.wms.inbound.application.command;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Consumed scm {@code inbound-expected.v1} payload (ADR-MONO-050 D1), decoded to the subset
 * wms reads. Codes ({@code supplierCode}, {@code destinationWarehouseCode}, line
 * {@code skuCode}) are resolved to wms master uuids inside the use-case.
 */
public record CreateScmInboundExpectationCommand(
        UUID poId,
        String poNumber,
        String supplierCode,
        String destinationWarehouseCode,
        String destinationNodeType,
        LocalDate expectedArrivalDate,
        List<Line> lines
) {
    public record Line(
            String skuCode,
            int expectedQty
    ) {}
}
