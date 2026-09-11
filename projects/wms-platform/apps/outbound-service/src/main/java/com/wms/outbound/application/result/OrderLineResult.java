package com.wms.outbound.application.result;

import java.util.UUID;

/**
 * Per-line entry on {@link OrderResult}.
 */
public record OrderLineResult(
        UUID orderLineId,
        int lineNo,
        UUID skuId,
        /** TASK-MONO-659 — 비정규화된 SKU 코드. 백필 전 기존 행은 null 이다. */
        String skuCode,
        UUID lotId,
        int qtyOrdered
) {
}
