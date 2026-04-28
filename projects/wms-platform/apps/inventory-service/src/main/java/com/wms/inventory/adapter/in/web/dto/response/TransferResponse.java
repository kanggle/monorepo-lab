package com.wms.inventory.adapter.in.web.dto.response;

import com.wms.inventory.application.result.TransferResult;
import com.wms.inventory.application.result.TransferView;
import com.wms.inventory.domain.model.TransferReasonCode;
import java.time.Instant;
import java.util.UUID;

public record TransferResponse(
        UUID transferId,
        UUID warehouseId,
        UUID sourceLocationId,
        UUID targetLocationId,
        UUID skuId,
        UUID lotId,
        int quantity,
        TransferReasonCode reasonCode,
        String reasonNote,
        Endpoint sourceInventory,
        Endpoint targetInventory,
        String actorId,
        Instant createdAt
) {

    public static TransferResponse from(TransferResult result) {
        TransferView t = result.transfer();
        return new TransferResponse(
                t.id(), t.warehouseId(),
                t.sourceLocationId(), t.targetLocationId(),
                t.skuId(), t.lotId(), t.quantity(),
                t.reasonCode(), t.reasonNote(),
                Endpoint.from(result.source()),
                Endpoint.from(result.target()),
                t.actorId(), t.createdAt());
    }

    public static TransferResponse fromView(TransferView t) {
        return new TransferResponse(
                t.id(), t.warehouseId(),
                t.sourceLocationId(), t.targetLocationId(),
                t.skuId(), t.lotId(), t.quantity(),
                t.reasonCode(), t.reasonNote(),
                null, null,
                t.actorId(), t.createdAt());
    }

    public record Endpoint(
            UUID id,
            int availableQty,
            int reservedQty,
            int damagedQty,
            long version
    ) {

        static Endpoint from(TransferResult.Endpoint e) {
            return new Endpoint(e.inventoryId(), e.availableQty(),
                    e.reservedQty(), e.damagedQty(), e.version());
        }
    }
}
