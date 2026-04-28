package com.wms.inventory.application.command;

import com.wms.inventory.domain.model.TransferReasonCode;
import java.util.UUID;

public record TransferStockCommand(
        UUID sourceLocationId,
        UUID targetLocationId,
        UUID skuId,
        UUID lotId,
        int quantity,
        TransferReasonCode reasonCode,
        String reasonNote,
        String actorId,
        String idempotencyKey
) {

    public TransferStockCommand {
        if (sourceLocationId == null || targetLocationId == null) {
            throw new IllegalArgumentException("sourceLocationId and targetLocationId are required");
        }
        if (skuId == null) {
            throw new IllegalArgumentException("skuId is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be > 0");
        }
        if (reasonCode == null) {
            throw new IllegalArgumentException("reasonCode is required");
        }
        if (actorId == null || actorId.isBlank()) {
            throw new IllegalArgumentException("actorId is required");
        }
    }
}
