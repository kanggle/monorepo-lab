package com.wms.inventory.adapter.in.web.dto.request;

import com.wms.inventory.domain.model.TransferReasonCode;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateTransferRequest(
        @NotNull UUID sourceLocationId,
        @NotNull UUID targetLocationId,
        @NotNull UUID skuId,
        UUID lotId,
        @NotNull @Min(1) Integer quantity,
        @NotNull TransferReasonCode reasonCode,
        String reasonNote
) {
}
