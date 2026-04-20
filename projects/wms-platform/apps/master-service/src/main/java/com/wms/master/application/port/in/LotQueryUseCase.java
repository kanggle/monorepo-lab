package com.wms.master.application.port.in;

import com.example.common.page.PageResult;
import com.wms.master.application.query.ListLotsQuery;
import com.wms.master.application.result.LotResult;
import java.util.UUID;

/**
 * Inbound port for Lot read-side operations.
 */
public interface LotQueryUseCase {

    LotResult findById(UUID id);

    PageResult<LotResult> list(ListLotsQuery query);
}
