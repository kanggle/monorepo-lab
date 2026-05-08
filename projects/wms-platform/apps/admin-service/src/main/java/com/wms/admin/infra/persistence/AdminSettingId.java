package com.wms.admin.infra.persistence;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/**
 * Composite key for admin_setting. Treats null {@code warehouseId} via
 * a sentinel UUID at the JPA layer to mirror the partial unique index in
 * V1__init.sql.
 */
public class AdminSettingId implements Serializable {

    public static final UUID NULL_SENTINEL = UUID.fromString("00000000-0000-0000-0000-000000000000");

    private String key;
    private UUID warehouseId;

    public AdminSettingId() {
    }

    public AdminSettingId(String key, UUID warehouseId) {
        this.key = key;
        this.warehouseId = warehouseId == null ? NULL_SENTINEL : warehouseId;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public UUID getWarehouseId() {
        return warehouseId;
    }

    public void setWarehouseId(UUID warehouseId) {
        this.warehouseId = warehouseId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AdminSettingId that)) return false;
        return Objects.equals(key, that.key) && Objects.equals(warehouseId, that.warehouseId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key, warehouseId);
    }
}
