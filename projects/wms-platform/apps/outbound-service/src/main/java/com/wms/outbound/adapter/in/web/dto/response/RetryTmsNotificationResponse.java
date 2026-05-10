package com.wms.outbound.adapter.in.web.dto.response;

import com.wms.outbound.application.result.RetryTmsNotificationResult;
import java.time.Instant;
import java.util.UUID;

/**
 * Response DTO for {@code POST /api/v1/outbound/shipments/{id}:retry-tms-notify}
 * (per {@code outbound-service-api.md} §4.3).
 */
public record RetryTmsNotificationResponse(
        UUID shipmentId,
        String tmsStatus,
        Instant tmsNotifiedAt,
        String trackingNo,
        String sagaState,
        Instant retriedAt,
        String retriedBy
) {

    public static RetryTmsNotificationResponse from(RetryTmsNotificationResult r) {
        return new RetryTmsNotificationResponse(
                r.shipmentId(),
                r.tmsStatus(),
                r.tmsNotifiedAt(),
                r.trackingNo(),
                r.sagaState(),
                r.retriedAt(),
                r.retriedBy());
    }
}
