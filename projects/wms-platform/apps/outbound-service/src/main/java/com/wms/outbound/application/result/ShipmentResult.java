package com.wms.outbound.application.result;

import java.time.Instant;
import java.util.UUID;

/**
 * Output record for {@code ConfirmShippingUseCase}. Mirrors
 * {@code outbound-service-api.md} §4.1 201 response.
 */
public record ShipmentResult(
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
}
