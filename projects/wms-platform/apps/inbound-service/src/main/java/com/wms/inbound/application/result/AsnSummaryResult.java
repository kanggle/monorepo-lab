package com.wms.inbound.application.result;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record AsnSummaryResult(
        UUID id,
        String asnNo,
        String source,
        UUID supplierPartnerId,
        UUID warehouseId,
        LocalDate expectedArriveDate,
        String status,
        long version,
        Instant createdAt
) {}
