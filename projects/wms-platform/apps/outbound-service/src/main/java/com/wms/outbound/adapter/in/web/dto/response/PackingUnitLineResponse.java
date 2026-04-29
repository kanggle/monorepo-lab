package com.wms.outbound.adapter.in.web.dto.response;

import com.wms.outbound.application.result.PackingUnitLineResult;
import java.util.UUID;

public record PackingUnitLineResponse(
        UUID packingUnitLineId,
        UUID orderLineId,
        UUID skuId,
        UUID lotId,
        int qty
) {

    public static PackingUnitLineResponse from(PackingUnitLineResult r) {
        return new PackingUnitLineResponse(
                r.packingUnitLineId(),
                r.orderLineId(),
                r.skuId(),
                r.lotId(),
                r.qty());
    }
}
