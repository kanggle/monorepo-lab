package com.wms.inventory.application.result;

import com.wms.inventory.domain.model.StockTransfer;
import com.wms.inventory.domain.model.TransferReasonCode;
import java.time.Instant;
import java.util.UUID;

public record TransferView(
        UUID id,
        UUID warehouseId,
        UUID sourceLocationId,
        UUID targetLocationId,
        UUID skuId,
        UUID lotId,
        int quantity,
        TransferReasonCode reasonCode,
        String reasonNote,
        String actorId,
        Instant createdAt
) {

    public static TransferView from(StockTransfer transfer) {
        return new TransferView(
                transfer.id(), transfer.warehouseId(),
                transfer.sourceLocationId(), transfer.targetLocationId(),
                transfer.skuId(), transfer.lotId(), transfer.quantity(),
                transfer.reasonCode(), transfer.reasonNote(),
                transfer.actorId(), transfer.createdAt());
    }
}
