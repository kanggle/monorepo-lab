package com.wms.inbound.application.result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AsnResult(
        UUID id,
        String asnNo,
        String source,
        UUID supplierPartnerId,
        UUID warehouseId,
        LocalDate expectedArriveDate,
        String notes,
        String status,
        long version,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        List<Line> lines
) {
    public record Line(
            UUID id,
            int lineNo,
            UUID skuId,
            UUID lotId,
            int expectedQty
    ) {}
}
