package com.wms.inventory.adapter.in.web.dto.response;

import com.wms.inventory.application.result.ReservationView;
import com.wms.inventory.domain.model.ReleasedReason;
import com.wms.inventory.domain.model.ReservationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID pickingRequestId,
        UUID warehouseId,
        ReservationStatus status,
        Instant expiresAt,
        ReleasedReason releasedReason,
        Instant confirmedAt,
        Instant releasedAt,
        List<Line> lines,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static ReservationResponse from(ReservationView v) {
        List<Line> lines = v.lines().stream().map(Line::from).toList();
        return new ReservationResponse(
                v.id(), v.pickingRequestId(), v.warehouseId(), v.status(),
                v.expiresAt(), v.releasedReason(), v.confirmedAt(), v.releasedAt(),
                lines, v.version(), v.createdAt(), v.updatedAt());
    }

    public record Line(UUID id, UUID inventoryId, UUID locationId, UUID skuId,
                       UUID lotId, int quantity) {
        static Line from(ReservationView.Line l) {
            return new Line(l.id(), l.inventoryId(), l.locationId(), l.skuId(),
                    l.lotId(), l.quantity());
        }
    }
}
