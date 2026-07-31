package com.wms.inventory.application.port.in;

import com.example.common.page.PageResult;
import com.wms.inventory.application.query.MovementListCriteria;
import com.wms.inventory.application.result.MovementView;

/**
 * Read-side queries against the W2 movement ledger.
 *
 * <p>Endpoints powered by this port:
 * {@code GET /api/v1/inventory/{inventoryId}/movements},
 * {@code GET /api/v1/inventory/movements}.
 */
public interface MovementQueryUseCase {

    PageResult<MovementView> list(MovementListCriteria criteria);
}
