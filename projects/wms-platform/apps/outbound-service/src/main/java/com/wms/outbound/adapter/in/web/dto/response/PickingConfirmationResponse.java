package com.wms.outbound.adapter.in.web.dto.response;

import com.wms.outbound.application.result.PickingConfirmationResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Wire shape per {@code outbound-service-api.md} §2.3.
 */
public record PickingConfirmationResponse(
        UUID pickingConfirmationId,
        UUID pickingRequestId,
        UUID orderId,
        String confirmedBy,
        Instant confirmedAt,
        String notes,
        List<PickingConfirmationLineResponse> lines,
        String orderStatus,
        String sagaState
) {

    public static PickingConfirmationResponse from(PickingConfirmationResult r) {
        return new PickingConfirmationResponse(
                r.pickingConfirmationId(),
                r.pickingRequestId(),
                r.orderId(),
                r.confirmedBy(),
                r.confirmedAt(),
                r.notes(),
                r.lines() == null ? List.of()
                        : r.lines().stream().map(PickingConfirmationLineResponse::from).toList(),
                r.orderStatus(),
                r.sagaState());
    }
}
