package com.wms.outbound.adapter.in.web.dto.response;

import com.wms.outbound.application.result.PackingUnitResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PackingUnitResponse(
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
        List<PackingUnitLineResponse> lines,
        String orderStatus,
        long version,
        Instant createdAt,
        Instant updatedAt
) {

    public static PackingUnitResponse from(PackingUnitResult r) {
        return new PackingUnitResponse(
                r.packingUnitId(),
                r.orderId(),
                r.cartonNo(),
                r.packingType(),
                r.weightGrams(),
                r.lengthMm(),
                r.widthMm(),
                r.heightMm(),
                r.notes(),
                r.status(),
                r.lines() == null ? List.of()
                        : r.lines().stream().map(PackingUnitLineResponse::from).toList(),
                r.orderStatus(),
                r.version(),
                r.createdAt(),
                r.updatedAt());
    }
}
