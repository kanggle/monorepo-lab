package com.wms.inbound.adapter.in.rest.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.UUID;

public record InstructPutawayRequest(
        @NotEmpty @Valid List<Line> lines,
        long version
) {
    public record Line(
            @NotNull UUID asnLineId,
            @NotNull UUID destinationLocationId,
            @Positive int qtyToPutaway
    ) {}
}
