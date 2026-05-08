package com.wms.admin.api.dashboard.dto;

import com.wms.admin.readmodel.outbound.ShipmentSummaryEntity;
import java.time.Instant;
import java.util.UUID;

public record ShipmentSummaryResponse(
        UUID shipmentId,
        UUID orderId,
        String orderNo,
        UUID warehouseId,
        String shipmentNo,
        String carrierCode,
        String trackingNo,
        Instant shippedAt,
        int totalQty,
        Instant lastEventAt,
        long version) {

    public static ShipmentSummaryResponse from(ShipmentSummaryEntity e) {
        return new ShipmentSummaryResponse(
                e.getShipmentId(), e.getOrderId(), e.getOrderNo(),
                e.getWarehouseId(), e.getShipmentNo(),
                e.getCarrierCode(), e.getTrackingNo(),
                e.getShippedAt(), e.getTotalQty(),
                e.getLastEventAt(), e.getVersion());
    }
}
