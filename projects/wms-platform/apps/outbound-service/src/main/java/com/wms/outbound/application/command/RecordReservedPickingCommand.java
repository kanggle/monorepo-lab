package com.wms.outbound.application.command;

import java.util.List;
import java.util.UUID;

/**
 * The parts of {@code wms.inventory.reserved.v1} that outbound needs in order to
 * materialise the {@link com.wms.outbound.domain.model.PickingRequest}.
 *
 * <p>ADR-MONO-066 (ACCEPTED, option B) put location assignment with
 * <b>inventory</b>: it already chooses a concrete {@code locationId} per line
 * while reserving — that is the decision which actually locks stock — and it
 * ships those ids back on the reply. Outbound records what inventory decided
 * rather than deciding again.
 *
 * <p>Deliberately a flat application-layer record and not the raw
 * {@code JsonNode}: the JSON shape belongs to the adapter, and the service that
 * consumes this must be testable without an envelope parser.
 */
public record RecordReservedPickingCommand(
        UUID sagaId,
        UUID pickingRequestId,
        UUID warehouseId,
        List<Line> lines
) {

    /**
     * One reserved line as inventory reported it.
     *
     * <p>🔴 There is no {@code orderLineId} here, and that is not an omission on
     * this record — {@code inventory.reserved} does not carry one. Inventory
     * keys its reservation lines by its own {@code reservationLineId}. Mapping
     * back to the order line is therefore outbound's job; see
     * {@code RecordReservedPickingService}.
     */
    public record Line(
            UUID skuId,
            UUID lotId,
            UUID locationId,
            int quantity
    ) {}
}
