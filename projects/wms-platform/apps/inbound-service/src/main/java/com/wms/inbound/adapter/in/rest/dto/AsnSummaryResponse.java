package com.wms.inbound.adapter.in.rest.dto;

import com.wms.inbound.application.result.AsnSummaryResult;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AsnSummaryResponse(
        UUID id,
        String asnNo,
        String source,
        UUID supplierPartnerId,
        UUID warehouseId,
        LocalDate expectedArriveDate,
        String status,
        long version,
        Instant createdAt
) {
    public static AsnSummaryResponse from(AsnSummaryResult r) {
        return new AsnSummaryResponse(r.id(), r.asnNo(), r.source(), r.supplierPartnerId(),
                r.warehouseId(), r.expectedArriveDate(), r.status(), r.version(), r.createdAt());
    }
}
