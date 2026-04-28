package com.wms.inventory.adapter.in.web.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import java.util.UUID;

public record ConfirmReservationRequest(
        @NotEmpty @Valid List<Line> lines,
        @NotNull @PositiveOrZero Long version
) {
    public record Line(
            @NotNull UUID reservationLineId,
            @Positive int shippedQuantity
    ) {
    }
}
