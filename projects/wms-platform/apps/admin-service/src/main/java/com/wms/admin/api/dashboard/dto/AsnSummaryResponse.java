package com.wms.admin.api.dashboard.dto;

import com.wms.admin.readmodel.inbound.AsnSummaryEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AsnSummaryResponse(
        UUID asnId,
        String asnNo,
        UUID warehouseId,
        UUID supplierPartnerId,
        String supplierName,
        String status,
        String source,
        LocalDate expectedArriveDate,
        int lineCount,
        Instant receivedAt,
        Instant closedAt,
        Instant lastEventAt,
        long version) {

    public static AsnSummaryResponse from(AsnSummaryEntity e) {
        return new AsnSummaryResponse(
                e.getAsnId(), e.getAsnNo(), e.getWarehouseId(),
                e.getSupplierPartnerId(), e.getSupplierName(),
                e.getStatus(), e.getSource(), e.getExpectedArriveDate(),
                e.getLineCount(), e.getReceivedAt(), e.getClosedAt(),
                e.getLastEventAt(), e.getVersion());
    }
}
