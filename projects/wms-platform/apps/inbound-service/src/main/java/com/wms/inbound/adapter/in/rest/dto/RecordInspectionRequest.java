package com.wms.inbound.adapter.in.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record RecordInspectionRequest(
        String notes,
        @NotEmpty @Valid List<Line> lines
) {
    public record Line(
            @NotNull UUID asnLineId,
            UUID lotId,
            String lotNo,
            @Min(0) int qtyPassed,
            @Min(0) int qtyDamaged,
            @Min(0) int qtyShort
    ) {}
}
