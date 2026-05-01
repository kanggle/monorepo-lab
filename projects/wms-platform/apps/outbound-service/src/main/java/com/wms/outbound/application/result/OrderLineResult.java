package com.wms.outbound.application.result;

import java.util.UUID;

/**
 * Per-line entry on {@link OrderResult}.
 */
public record OrderLineResult(
        UUID orderLineId,
        int lineNo,
        UUID skuId,
        UUID lotId,
        int qtyOrdered
) {
}
