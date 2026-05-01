package com.wms.outbound.application.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Output record for {@code ConfirmPickingUseCase}. Mirrors the
 * {@code outbound-service-api.md} §2.3 201 response.
 */
public record PickingConfirmationResult(
        UUID pickingConfirmationId,
        UUID pickingRequestId,
        UUID orderId,
        String confirmedBy,
        Instant confirmedAt,
        String notes,
        List<PickingConfirmationLineResult> lines,
        String orderStatus,
        String sagaState
) {
}
