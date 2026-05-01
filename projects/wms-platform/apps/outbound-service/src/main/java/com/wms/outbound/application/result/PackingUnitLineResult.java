package com.wms.outbound.application.result;

import java.util.UUID;

public record PackingUnitLineResult(
        UUID packingUnitLineId,
        UUID orderLineId,
        UUID skuId,
        UUID lotId,
        int qty
) {
}
