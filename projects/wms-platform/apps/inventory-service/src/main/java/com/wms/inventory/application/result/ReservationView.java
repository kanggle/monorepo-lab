package com.wms.inventory.application.result;

import com.wms.inventory.domain.model.ReleasedReason;
import com.wms.inventory.domain.model.Reservation;
import com.wms.inventory.domain.model.ReservationLine;
import com.wms.inventory.domain.model.ReservationStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReservationView(
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

    public ReservationView {
        lines = List.copyOf(lines);
    }

    public static ReservationView from(Reservation r) {
        List<Line> lines = r.lines().stream().map(Line::from).toList();
        return new ReservationView(
                r.id(), r.pickingRequestId(), r.warehouseId(), r.status(),
                r.expiresAt(), r.releasedReason(), r.confirmedAt(), r.releasedAt(),
                lines, r.version(), r.createdAt(), r.updatedAt());
    }

    public record Line(UUID id, UUID inventoryId, UUID locationId, UUID skuId,
                       UUID lotId, int quantity) {
        public static Line from(ReservationLine l) {
            return new Line(l.id(), l.inventoryId(), l.locationId(), l.skuId(),
                    l.lotId(), l.quantity());
        }
    }
}
