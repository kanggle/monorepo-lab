package com.wms.outbound.adapter.in.web.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Per-line entry on {@link CreateOrderRequest}.
 */
public record CreateOrderLineRequest(
        @Min(1) int lineNo,
        @NotNull UUID skuId,
        UUID lotId,
        @Min(1) int qtyOrdered
) {
}
