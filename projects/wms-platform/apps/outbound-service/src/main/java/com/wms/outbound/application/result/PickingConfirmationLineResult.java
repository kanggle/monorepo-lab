package com.wms.outbound.application.result;

import java.util.UUID;

public record PickingConfirmationLineResult(
        UUID pickingConfirmationLineId,
        UUID orderLineId,
        UUID skuId,
        UUID lotId,
        UUID actualLocationId,
        int qtyConfirmed
) {
}
