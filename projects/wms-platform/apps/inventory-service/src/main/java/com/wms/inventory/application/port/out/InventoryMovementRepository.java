package com.wms.inventory.application.port.out;

import com.wms.inventory.application.query.MovementListCriteria;
import com.wms.inventory.application.result.MovementView;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.domain.model.InventoryMovement;

/**
 * Append-only port for {@link InventoryMovement}. The adapter exposes only
 * {@code save} and read methods — DB-level role grants reject UPDATE/DELETE
 * (W2). The Java surface mirrors that contract: no update / delete methods.
 */
public interface InventoryMovementRepository {

    void save(InventoryMovement movement);

    PageView<MovementView> list(MovementListCriteria criteria);
}
