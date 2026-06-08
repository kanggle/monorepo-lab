package com.wms.outbound.adapter.in.web.dto.response;

import com.wms.outbound.application.result.SagaResult;
import java.time.Instant;
import java.util.UUID;

/**
 * Wire shape of {@code GET /orders/{id}/saga} (outbound-service-api.md §5.1).
 */
public record SagaResponse(
        UUID sagaId,
        UUID orderId,
        String state,
        String failureReason,
        Instant startedAt,
        Instant lastTransitionAt,
        long version
) {
    public static SagaResponse from(SagaResult r) {
        return new SagaResponse(
                r.sagaId(),
                r.orderId(),
                r.state(),
                r.failureReason(),
                r.startedAt(),
                r.lastTransitionAt(),
                r.version());
    }
}
