package com.wms.inventory.adapter.in.web.dto.request;

import com.wms.inventory.domain.model.ReleasedReason;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ReleaseReservationRequest(
        @NotNull ReleasedReason reason,
        @PositiveOrZero Long version
) {
}
