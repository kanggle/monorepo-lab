package com.wms.master.application.query;

import com.example.common.page.PageQuery;
import java.util.Set;

/**
 * Read-side query envelope for listing zones under a warehouse.
 *
 * @param criteria            parent-warehouse-scoped filters (warehouse id, status, type)
 * @param pageQuery           page/sort
 * @param scopeWarehouseCodes ABAC data-scope confinement (ADR-MONO-025): the
 *                            operator's deliberately-scoped warehouse codes, or
 *                            {@code null} for the net-zero (unrestricted /
 *                            unscoped) path. Because a zone list is already
 *                            confined to one warehouse by the nested route, the
 *                            service applies this as a single gate (403 when the
 *                            parent warehouse code is out of scope), not a row
 *                            filter. Resolved by the controller via
 *                            {@code DataScopeSupport.warehouseScopeCodes}.
 */
public record ListZonesQuery(
        ListZonesCriteria criteria,
        PageQuery pageQuery,
        Set<String> scopeWarehouseCodes) {

    /** Net-zero (unrestricted) listing — no data-scope gate. */
    public ListZonesQuery(ListZonesCriteria criteria, PageQuery pageQuery) {
        this(criteria, pageQuery, null);
    }
}
