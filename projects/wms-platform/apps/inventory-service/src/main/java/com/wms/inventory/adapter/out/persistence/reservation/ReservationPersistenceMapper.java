package com.wms.inventory.adapter.out.persistence.reservation;

import com.wms.inventory.domain.model.ReleasedReason;
import com.wms.inventory.domain.model.Reservation;
import com.wms.inventory.domain.model.ReservationLine;
import com.wms.inventory.domain.model.ReservationStatus;
import java.util.List;

final class ReservationPersistenceMapper {

    private ReservationPersistenceMapper() {
    }

    static Reservation toDomain(ReservationJpaEntity e) {
        List<ReservationLine> lines = e.getLines().stream()
                .map(ReservationPersistenceMapper::toDomainLine)
                .toList();
        ReleasedReason reason = e.getReleasedReason() == null
                ? null : ReleasedReason.valueOf(e.getReleasedReason());
        return Reservation.restore(
                e.getId(), e.getPickingRequestId(), e.getWarehouseId(),
                lines, ReservationStatus.valueOf(e.getStatus()),
                e.getExpiresAt(), reason,
                e.getConfirmedAt(), e.getReleasedAt(),
                e.getVersion(),
                e.getCreatedAt(), e.getCreatedBy(),
                e.getUpdatedAt(), e.getUpdatedBy());
    }

    private static ReservationLine toDomainLine(ReservationLineJpaEntity l) {
        return new ReservationLine(
                l.getId(), l.getReservationId(), l.getInventoryId(),
                l.getLocationId(), l.getSkuId(), l.getLotId(), l.getQuantity());
    }

    static ReservationJpaEntity toEntity(Reservation r) {
        List<ReservationLineJpaEntity> lines = r.lines().stream()
                .map(ReservationPersistenceMapper::toEntityLine)
                .toList();
        String reason = r.releasedReason() == null ? null : r.releasedReason().name();
        return new ReservationJpaEntity(
                r.id(), r.pickingRequestId(), r.warehouseId(),
                r.status().name(), r.expiresAt(), reason,
                r.confirmedAt(), r.releasedAt(),
                r.version(),
                r.createdAt(), r.createdBy(),
                r.updatedAt(), r.updatedBy(),
                lines);
    }

    private static ReservationLineJpaEntity toEntityLine(ReservationLine l) {
        return new ReservationLineJpaEntity(
                l.id(), l.reservationId(), l.inventoryId(),
                l.locationId(), l.skuId(), l.lotId(), l.quantity());
    }
}
