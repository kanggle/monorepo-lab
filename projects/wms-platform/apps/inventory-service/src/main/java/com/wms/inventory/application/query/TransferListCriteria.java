package com.wms.inventory.application.query;

import com.wms.inventory.domain.model.TransferReasonCode;
import java.time.Instant;
import java.util.UUID;

public record TransferListCriteria(
        UUID warehouseId,
        UUID sourceLocationId,
        UUID targetLocationId,
        UUID skuId,
        TransferReasonCode reasonCode,
        Instant createdAfter,
        Instant createdBefore,
        int page,
        int size
) {
}
