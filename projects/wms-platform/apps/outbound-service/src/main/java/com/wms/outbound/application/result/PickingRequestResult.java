package com.wms.outbound.application.result;

import java.time.Instant;
import java.util.UUID;

/**
 * Read-side output record for {@code QueryPickingRequestUseCase} (AC-04 of
 * TASK-BE-040 — controller layer must consume in-ports only, never the
 * persistence out-port directly).
 *
 * <p>Mirrors the {@code PickingRequest} aggregate fields the REST layer needs
 * to resolve a path-variable id into the contextual {@code orderId} for the
 * confirm-picking flow. Lines are intentionally omitted: the controller does
 * not need them for confirm-picking dispatch.
 */
public record PickingRequestResult(
        UUID pickingRequestId,
        UUID orderId,
        UUID sagaId,
        UUID warehouseId,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
