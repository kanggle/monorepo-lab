package com.wms.inventory.application.port.out;

import com.wms.inventory.application.query.InventoryListCriteria;
import com.wms.inventory.application.result.InventoryView;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.domain.model.Inventory;
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
