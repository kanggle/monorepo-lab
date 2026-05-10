package com.wms.outbound.application.result;

import java.time.Instant;
import java.util.UUID;

/**
 * Output record for {@code RetryTmsNotificationUseCase}. Mirrors
 * {@code outbound-service-api.md} §4.3 200 response.
 */
public record RetryTmsNotificationResult(
        UUID shipmentId,
        String tmsStatus,
        Instant tmsNotifiedAt,
        String trackingNo,
        String sagaState,
        Instant retriedAt,
        String retriedBy
) {
}
