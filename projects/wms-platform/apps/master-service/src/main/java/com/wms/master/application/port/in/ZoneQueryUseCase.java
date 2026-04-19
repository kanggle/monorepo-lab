package com.wms.master.application.port.in;

import com.example.common.page.PageResult;
import com.wms.master.application.query.ListZonesQuery;
import com.wms.master.application.result.ZoneResult;
import java.util.UUID;

/**
 * Inbound port for Zone read-side operations.
 */
public interface ZoneQueryUseCase {

    ZoneResult findById(UUID id);

    PageResult<ZoneResult> list(ListZonesQuery query);
}
