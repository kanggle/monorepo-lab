package com.wms.outbound.adapter.in.web.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreatePackingUnitLineRequest(
        @NotNull UUID orderLineId,
        @NotNull UUID skuId,
        UUID lotId,
        @Min(1) int qty
) {
}
