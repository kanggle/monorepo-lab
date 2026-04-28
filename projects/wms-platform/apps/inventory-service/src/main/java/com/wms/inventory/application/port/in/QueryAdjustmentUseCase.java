package com.wms.inventory.application.port.in;

import com.wms.inventory.application.query.AdjustmentListCriteria;
import com.wms.inventory.application.result.AdjustmentView;
import com.wms.inventory.application.result.PageView;
import java.util.Optional;
import java.util.UUID;

public interface QueryAdjustmentUseCase {

    Optional<AdjustmentView> findById(UUID id);

    PageView<AdjustmentView> list(AdjustmentListCriteria criteria);
}
