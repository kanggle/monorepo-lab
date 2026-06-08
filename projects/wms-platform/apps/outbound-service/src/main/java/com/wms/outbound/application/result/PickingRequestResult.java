package com.wms.outbound.application.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-side output record for {@code QueryPickingRequestUseCase} (AC-04 of
 * TASK-BE-040 — controller layer must consume in-ports only, never the
 * persistence out-port directly).
 *
 * <p>Mirrors the {@code PickingRequest} aggregate fields the REST layer needs.
 * The {@code lines} field (added by TASK-BE-343) carries the planned picking
 * lines — including {@code locationId} and {@code qtyToPick} — that the §2.3
 * pick-confirmation body requires when discovered via the §2.4 by-order read.
 *
 * <p>Existing callers (e.g. {@code ConfirmPickingService}) that do not use
 * {@code lines} are unaffected — the field is additive.
 */
public record PickingRequestResult(
        UUID pickingRequestId,
        UUID orderId,
        UUID sagaId,
        UUID warehouseId,
        String status,
        List<PickingRequestLineResult> lines,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
}
