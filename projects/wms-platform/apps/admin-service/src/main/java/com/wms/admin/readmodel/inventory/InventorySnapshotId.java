package com.wms.admin.readmodel.inventory;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite key for {@code admin_inventory_snapshot}. Treats null lot_id via a
 * sentinel UUID to mirror the {@code admin_setting} V1 pattern. The sentinel
 * collapse is internal — adapters and DTOs always surface {@code null} when the
 * stored value is {@link #NULL_SENTINEL}.
 */
public class InventorySnapshotId implements Serializable {

    public static final UUID NULL_SENTINEL = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private UUID locationId;
    private UUID skuId;
    private UUID lotId;

    public InventorySnapshotId() {
    }

    public InventorySnapshotId(UUID locationId, UUID skuId, UUID lotId) {
        this.locationId = locationId;
        this.skuId = skuId;
        this.lotId = lotId == null ? NULL_SENTINEL : lotId;
    }

    public UUID getLocationId() {
        return locationId;
    }

    public void setLocationId(UUID locationId) {
        this.locationId = locationId;
    }

    public UUID getSkuId() {
        return skuId;
    }

    public void setSkuId(UUID skuId) {
        this.skuId = skuId;
    }

    public UUID getLotId() {
        return lotId;
    }

    public void setLotId(UUID lotId) {
        this.lotId = lotId == null ? NULL_SENTINEL : lotId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof InventorySnapshotId that)) return false;
        return Objects.equals(locationId, that.locationId)
                && Objects.equals(skuId, that.skuId)
                && Objects.equals(lotId, that.lotId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locationId, skuId, lotId);
    }
}
