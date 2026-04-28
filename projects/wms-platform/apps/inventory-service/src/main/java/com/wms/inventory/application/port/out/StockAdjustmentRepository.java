package com.wms.inventory.application.port.out;

import com.wms.inventory.application.query.AdjustmentListCriteria;
import com.wms.inventory.application.result.AdjustmentView;
import com.wms.inventory.application.result.PageView;
import com.wms.inventory.domain.model.StockAdjustment;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence port for {@link StockAdjustment}. Adjustments are immutable
 * post-create — no update / delete methods.
 */
public interface StockAdjustmentRepository {

    StockAdjustment insert(StockAdjustment adjustment);

    Optional<StockAdjustment> findById(UUID id);

    PageView<AdjustmentView> list(AdjustmentListCriteria criteria);
}
