package com.wms.outbound.adapter.in.web.dto.response;

import com.wms.outbound.application.result.PickingRequestLineResult;
import java.util.UUID;

/**
 * Wire shape for a single picking-request line, per
 * {@code outbound-service-api.md} §2.4 (and §2.1/§2.2 picking shape).
 */
public record PickingRequestLineResponse(
        UUID pickingRequestLineId,
        UUID orderLineId,
        UUID skuId,
        UUID lotId,
        UUID locationId,
        int qtyToPick
) {

    public static PickingRequestLineResponse from(PickingRequestLineResult r) {
        return new PickingRequestLineResponse(
                r.pickingRequestLineId(),
                r.orderLineId(),
                r.skuId(),
                r.lotId(),
                r.locationId(),
                r.qtyToPick());
    }
}
