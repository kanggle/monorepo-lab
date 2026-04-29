package com.wms.outbound.adapter.in.web.dto.response;

import com.wms.outbound.application.result.OrderLineResult;
import java.util.UUID;

public record OrderLineResponse(
        UUID orderLineId,
        int lineNo,
        UUID skuId,
        UUID lotId,
        int qtyOrdered
) {

    public static OrderLineResponse from(OrderLineResult r) {
        return new OrderLineResponse(
                r.orderLineId(),
                r.lineNo(),
                r.skuId(),
                r.lotId(),
                r.qtyOrdered());
    }
}
