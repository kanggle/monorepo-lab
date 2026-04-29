package com.wms.outbound.application.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Output record for {@code CreatePackingUnitUseCase} and
 * {@code SealPackingUnitUseCase}. Mirrors {@code outbound-service-api.md} §3.1
 * 201 response and §3.2 200 response shapes.
 */
public record PackingUnitResult(
        UUID packingUnitId,
        UUID orderId,
        String cartonNo,
        String packingType,
        Integer weightGrams,
        Integer lengthMm,
        Integer widthMm,
        Integer heightMm,
        String notes,
        String status,
        List<PackingUnitLineResult> lines,
        String orderStatus,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
