package com.wms.outbound.application.result;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-side output record for {@code QuerySagaUseCase} (the {@code GET
 * /orders/{id}/saga} endpoint, outbound-service-api.md §5.1).
 */
public record SagaResult(
        UUID sagaId,
        UUID orderId,
        String state,
        String failureReason,
        Instant startedAt,
        Instant lastTransitionAt,
        long version
) {
}
