package com.wms.master.application.port.in;

import com.example.common.page.PageResult;
import com.wms.master.application.query.ListLocationsQuery;
import com.wms.master.application.result.LocationResult;
import java.util.UUID;

/**
 * Inbound port for Location read-side operations.
 */
public interface LocationQueryUseCase {

    LocationResult findById(UUID id);

    PageResult<LocationResult> list(ListLocationsQuery query);
}
