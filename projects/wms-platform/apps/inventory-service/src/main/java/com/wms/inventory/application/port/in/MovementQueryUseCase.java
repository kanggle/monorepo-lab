package com.wms.inventory.application.port.in;

import com.wms.inventory.application.query.MovementListCriteria;
import com.wms.inventory.application.result.MovementView;
import com.wms.inventory.application.result.PageView;

/**
 * Read-side queries against the W2 movement ledger.
 *
 * <p>Endpoints powered by this port:
 * {@code GET /api/v1/inventory/{inventoryId}/movements},
 * {@code GET /api/v1/inventory/movements}.
 */
public interface MovementQueryUseCase {

    PageView<MovementView> list(MovementListCriteria criteria);
}
