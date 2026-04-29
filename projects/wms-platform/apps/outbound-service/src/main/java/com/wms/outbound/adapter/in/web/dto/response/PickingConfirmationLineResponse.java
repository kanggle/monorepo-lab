package com.wms.outbound.adapter.in.web.dto.response;

import com.wms.outbound.application.result.PickingConfirmationLineResult;
import java.util.UUID;

public record PickingConfirmationLineResponse(
        UUID pickingConfirmationLineId,
        UUID orderLineId,
        UUID skuId,
        UUID lotId,
        UUID actualLocationId,
        int qtyConfirmed
) {

    public static PickingConfirmationLineResponse from(PickingConfirmationLineResult r) {
        return new PickingConfirmationLineResponse(
                r.pickingConfirmationLineId(),
                r.orderLineId(),
                r.skuId(),
                r.lotId(),
                r.actualLocationId(),
                r.qtyConfirmed());
    }
}
