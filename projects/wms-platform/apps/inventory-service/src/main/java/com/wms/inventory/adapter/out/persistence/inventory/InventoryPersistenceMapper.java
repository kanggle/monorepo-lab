package com.wms.inventory.adapter.out.persistence.inventory;

import com.wms.inventory.domain.model.Inventory;

final class InventoryPersistenceMapper {

    private InventoryPersistenceMapper() {
    }

    static Inventory toDomain(InventoryJpaEntity e) {
        return Inventory.restore(
                e.getId(), e.getWarehouseId(), e.getLocationId(), e.getSkuId(), e.getLotId(),
                e.getAvailableQty(), e.getReservedQty(), e.getDamagedQty(),
                e.getLastMovementAt(), e.getVersion(),
                e.getCreatedAt(), e.getCreatedBy(),
                e.getUpdatedAt(), e.getUpdatedBy());
    }

    static InventoryJpaEntity toEntity(Inventory i) {
        return new InventoryJpaEntity(
                i.id(), i.warehouseId(), i.locationId(), i.skuId(), i.lotId(),
                i.availableQty(), i.reservedQty(), i.damagedQty(),
                i.lastMovementAt(), i.version(),
                i.createdAt(), i.createdBy(),
                i.updatedAt(), i.updatedBy());
    }
}
