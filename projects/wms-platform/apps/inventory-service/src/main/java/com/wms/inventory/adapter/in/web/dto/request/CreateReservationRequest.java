package com.wms.inventory.adapter.in.web.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

public record CreateReservationRequest(
        @NotNull UUID pickingRequestId,
        @NotNull UUID warehouseId,
        @NotEmpty @Valid List<Line> lines,
        @Min(1) Integer ttlSeconds
) {
    public record Line(
            @NotNull UUID inventoryId,
            @Positive int quantity
    ) {
    }
}
