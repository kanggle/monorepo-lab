package com.wms.inventory.adapter.in.web.dto.request;

import com.wms.inventory.domain.model.Bucket;
import com.wms.inventory.domain.model.ReasonCode;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateAdjustmentRequest(
        @NotNull UUID inventoryId,
        @NotNull Bucket bucket,
        @NotNull Integer delta,
        @NotNull ReasonCode reasonCode,
        @NotNull String reasonNote
) {
}
