package com.wms.master.application.query;

import com.example.common.page.PageQuery;
import java.util.Set;

/**
 * Read-side query envelope for the flat (cross-warehouse) location list.
 *
 * @param criteria            optional filters (warehouse, zone, type, code, status)
 * @param pageQuery           page/sort
 * @param scopeWarehouseCodes ABAC data-scope confinement (ADR-MONO-025): the
 *                            operator's deliberately-scoped warehouse codes, or
 *                            {@code null} for the net-zero (unrestricted /
 *                            unscoped) path. Because the location list is
 *                            cross-warehouse, the adapter applies this as a
 *                            per-row DB filter (a {@code warehouseId IN (subquery
 *                            on warehouse code)}), not a gate. Resolved by the
 *                            controller via {@code DataScopeSupport.warehouseScopeCodes}.
 */
public record ListLocationsQuery(
        ListLocationsCriteria criteria,
        PageQuery pageQuery,
        Set<String> scopeWarehouseCodes) {

    /** Net-zero (unrestricted) listing — no data-scope filter. */
    public ListLocationsQuery(ListLocationsCriteria criteria, PageQuery pageQuery) {
        this(criteria, pageQuery, null);
    }
}
