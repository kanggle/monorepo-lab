package com.example.product.infrastructure.reconciliation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Per-{@code inventoryId} wms availableQty trajectory — the delta ledger for
 * reconciliation (ADR-MONO-022 §D4 v2(b), Option B). On each warehouse-origin event
 * the applied stock delta is {@code (new availableQty − stored)}; this row stores the
 * last-known value so re-delivery and any adjustment reason are handled correctly.
 */
@Entity
@Table(name = "wms_inventory_available")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WmsInventoryAvailableEntity {

    @Id
    @Column(name = "inventory_id", columnDefinition = "uuid")
    private UUID inventoryId;

    @Column(name = "sku_id", columnDefinition = "uuid", nullable = false)
    private UUID skuId;

    @Column(name = "available_qty", nullable = false)
    private int availableQty;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static WmsInventoryAvailableEntity of(UUID inventoryId, UUID skuId, int availableQty, Instant now) {
        WmsInventoryAvailableEntity e = new WmsInventoryAvailableEntity();
        e.inventoryId = inventoryId;
        e.skuId = skuId;
        e.availableQty = availableQty;
        e.updatedAt = now;
        return e;
    }

    public void update(int availableQty, Instant now) {
        this.availableQty = availableQty;
        this.updatedAt = now;
    }
}
