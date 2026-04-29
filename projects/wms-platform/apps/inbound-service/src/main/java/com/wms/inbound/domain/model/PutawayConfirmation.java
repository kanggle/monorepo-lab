package com.wms.inbound.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only confirmation record. One row per CONFIRMED PutawayLine.
 * Never updated or deleted after creation (W2 / domain-model.md §4).
 */
public record PutawayConfirmation(
        UUID id,
        UUID putawayInstructionId,
        UUID putawayLineId,
        UUID skuId,
        UUID lotId,
        UUID plannedLocationId,
        UUID actualLocationId,
        int qtyConfirmed,
        String confirmedBy,
        Instant confirmedAt
) {
}
