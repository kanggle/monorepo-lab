package com.wms.inventory.application.port.in;

import com.example.common.page.PageResult;
import com.wms.inventory.application.query.InventoryListCriteria;
import com.wms.inventory.application.result.InventoryView;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-side queries against {@link com.wms.inventory.domain.model.Inventory}.
 *
 * <p>Endpoints powered by this port:
 * {@code GET /api/v1/inventory},
 * {@code GET /api/v1/inventory/{id}},
 * {@code GET /api/v1/inventory/by-key}.
 */
public interface QueryInventoryUseCase {

    PageResult<InventoryView> list(InventoryListCriteria criteria);

    Optional<InventoryView> findById(UUID id);

    Optional<InventoryView> findByKey(UUID locationId, UUID skuId, UUID lotId);
}
