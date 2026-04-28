package com.wms.inventory.application.query;

import com.wms.inventory.domain.model.ReservationStatus;
import java.time.Instant;
import java.util.UUID;

public record ReservationListCriteria(
        ReservationStatus status,
        UUID warehouseId,
        UUID pickingRequestId,
        Instant expiresAfter,
        Instant expiresBefore,
        int page,
        int size
) {
    public ReservationListCriteria {
        if (page < 0) {
            throw new IllegalArgumentException("page must be >= 0");
        }
        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException("size must be in (0, 100]");
        }
    }
}
