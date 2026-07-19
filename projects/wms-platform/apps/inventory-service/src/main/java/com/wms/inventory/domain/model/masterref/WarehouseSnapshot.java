package com.wms.inventory.domain.model.masterref;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Local read-model snapshot of a master Warehouse.
 *
 * <p>Populated by {@code MasterWarehouseConsumer} from
 * {@code wms.master.warehouse.v1} events. Consumed by the use-case layer to
 * resolve a warehouse's business {@code warehouseCode} from its uuid — notably
 * to enrich the {@code inventory.low-stock-detected} alert with
 * {@code warehouseCode} so the cross-project scm demand-planning consumer can
 * address a replenishment PO by code (ADR-MONO-050 D9).
 */
public record WarehouseSnapshot(
        UUID id,
        String warehouseCode,
        Status status,
        Instant cachedAt,
        long masterVersion
) {

    public WarehouseSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(warehouseCode, "warehouseCode");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(cachedAt, "cachedAt");
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public enum Status {
        ACTIVE,
        INACTIVE
    }
}
