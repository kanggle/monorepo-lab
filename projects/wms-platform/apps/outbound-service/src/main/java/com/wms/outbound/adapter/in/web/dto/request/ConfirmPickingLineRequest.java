package com.wms.outbound.adapter.in.web.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * One element of {@link ConfirmPickingRequest#lines()}.
 */
public record ConfirmPickingLineRequest(
        @NotNull UUID orderLineId,
        @NotNull UUID skuId,
        UUID lotId,
        @NotNull UUID actualLocationId,
        @Min(1) int qtyConfirmed
) {
}
