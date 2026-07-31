package com.wms.inventory.application.port.in;

import com.example.common.page.PageResult;
import com.wms.inventory.application.query.AdjustmentListCriteria;
import com.wms.inventory.application.result.AdjustmentView;
import java.util.Optional;
import java.util.UUID;

public interface QueryAdjustmentUseCase {

    Optional<AdjustmentView> findById(UUID id);

    PageResult<AdjustmentView> list(AdjustmentListCriteria criteria);
}
