package com.wms.inventory.adapter.in.web.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record WriteOffDamagedRequest(
        @NotNull @Min(1) Integer quantity,
        @NotNull String reasonNote
) {
}
