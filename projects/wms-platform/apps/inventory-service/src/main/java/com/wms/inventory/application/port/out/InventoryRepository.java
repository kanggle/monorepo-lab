package com.wms.inventory.application.port.out;

import com.wms.inventory.application.query.InventoryListCriteria;
import com.wms.inventory.application.result.InventoryView;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.domain.model.Inventory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for the {@link Inventory} aggregate.
 *
 * <p>The use-case loads-then-saves; the adapter implements optimistic
 * locking on the {@code version} column. Read-side projections enrich
 * domain rows with {@code MasterReadModel} display fields.
 */
public interface InventoryRepository {

    Optional<Inventory> findById(UUID id);

    Optional<Inventory> findByKey(UUID locationId, UUID skuId, UUID lotId);

    /**
     * Candidate stock rows for the {@code (warehouseId, skuId, lotId)} natural
     * key with {@code available_qty > 0}, ordered by {@code available_qty DESC,
     * id ASC} (deterministic greatest-available-first).
     *
     * <p>Used by {@code PickingRequestedConsumer} to resolve a reservation
     * line whose {@code locationId} is null (the v1 norm — the picking source
     * location is assigned later). {@code lotId} null matches rows with
     * {@code lot_id IS NULL}. Returns an empty list when no stock row exists —
     * the consumer treats that as zero available (shortfall → backorder),
     * never as a not-found error.
     */
    List<Inventory> findAvailableByWarehouseSkuLot(UUID warehouseId, UUID skuId, UUID lotId);

    Optional<InventoryView> findViewById(UUID id);

    Optional<InventoryView> findViewByKey(UUID locationId, UUID skuId, UUID lotId);

    PageView<InventoryView> listViews(InventoryListCriteria criteria);

    /**
     * Persist a freshly-created Inventory row (no version check).
     */
    Inventory insert(Inventory inventory);

    /**
     * Update an existing Inventory row with a version-checked SQL UPDATE.
     * Throws {@code OptimisticLockingFailureException} on version mismatch.
     */
    Inventory updateWithVersionCheck(Inventory inventory);
}
