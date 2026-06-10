package com.wms.master.application.port.in;

import com.example.common.page.PageResult;
import com.wms.master.application.query.ListLocationsQuery;
import com.wms.master.application.result.LocationResult;
import java.util.Collection;
import java.util.UUID;

/**
 * Inbound port for Location read-side operations.
 */
public interface LocationQueryUseCase {

    LocationResult findById(UUID id);

    /**
     * ADR-MONO-025 data-scope-aware read: when {@code scopeWarehouseCodes} is
     * non-empty and the location's parent warehouse code is outside it, raises
     * {@link com.wms.master.domain.exception.DataScopeForbiddenException} (403).
     * {@code null}/empty = net-zero (no confinement). The 1-arg overload is the
     * net-zero call. Both are {@code @PreAuthorize}'d in the implementation — the
     * 1-arg is NOT an interface default, to avoid bypassing method security.
     */
    LocationResult findById(UUID id, Collection<String> scopeWarehouseCodes);

    PageResult<LocationResult> list(ListLocationsQuery query);
}
