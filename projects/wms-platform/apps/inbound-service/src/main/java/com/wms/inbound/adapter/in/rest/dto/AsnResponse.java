package com.wms.inbound.adapter.in.rest.dto;

import com.wms.inbound.application.result.AsnResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AsnResponse(
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

    public static AsnResponse from(AsnResult r) {
        List<Line> lines = r.lines().stream()
                .map(l -> new Line(l.id(), l.lineNo(), l.skuId(), l.lotId(), l.expectedQty()))
                .toList();
        return new AsnResponse(r.id(), r.asnNo(), r.source(), r.supplierPartnerId(),
                r.warehouseId(), r.expectedArriveDate(), r.notes(), r.status(),
                r.version(), r.createdAt(), r.createdBy(), r.updatedAt(), lines);
    }
}
