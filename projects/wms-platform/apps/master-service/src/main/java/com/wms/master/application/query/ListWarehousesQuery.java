package com.wms.master.application.query;

import com.example.common.page.PageQuery;
import java.util.Set;

/**
 * Read-side query envelope for listing warehouses.
 *
 * @param criteria            user-facing filters (status, text)
 * @param pageQuery           page/sort
 * @param scopeWarehouseCodes ABAC data-scope confinement (ADR-MONO-025): the set
 *                            of warehouse codes a deliberately-scoped operator may
 *                            see, or {@code null} for unrestricted / unscoped
 *                            operators (net-zero — no filter). Resolved by the
 *                            controller from the operator's {@code data_scope} /
 *                            {@code org_scope} claim via
 *                            {@link com.example.security.jwt.AbacDataScope}.
 */
public record ListWarehousesQuery(
        WarehouseListCriteria criteria,
        PageQuery pageQuery,
        Set<String> scopeWarehouseCodes) {

    /** Net-zero (unrestricted) listing — no data-scope confinement. */
    public ListWarehousesQuery(WarehouseListCriteria criteria, PageQuery pageQuery) {
        this(criteria, pageQuery, null);
    }
}
