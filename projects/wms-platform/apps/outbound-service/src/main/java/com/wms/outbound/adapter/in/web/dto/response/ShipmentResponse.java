package com.wms.outbound.adapter.in.web.dto.response;

import com.wms.outbound.application.result.ShipmentResult;
import java.time.Instant;
import java.util.UUID;

public record ShipmentResponse(
        UUID shipmentId,
        String shipmentNo,
        UUID orderId,
        String orderNo,
        String carrierCode,
        String trackingNo,
        Instant shippedAt,
        String tmsStatus,
        Instant tmsNotifiedAt,
        String orderStatus,
        String sagaState,
        long version,
        Instant createdAt,
        String createdBy
) {

    public static ShipmentResponse from(ShipmentResult r) {
        return new ShipmentResponse(
                r.shipmentId(),
                r.shipmentNo(),
                r.orderId(),
                r.orderNo(),
                r.carrierCode(),
                r.trackingNo(),
                r.shippedAt(),
                r.tmsStatus(),
                r.tmsNotifiedAt(),
                r.orderStatus(),
                r.sagaState(),
                r.version(),
                r.createdAt(),
                r.createdBy());
    }
}
