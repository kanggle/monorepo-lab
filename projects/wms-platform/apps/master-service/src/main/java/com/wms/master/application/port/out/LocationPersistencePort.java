package com.wms.master.application.port.out;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.query.ListLocationsCriteria;
import com.wms.master.domain.model.Location;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for Location persistence. Implemented by an adapter in
 * {@code adapter.out.persistence}. Uses domain / shared types only — no JPA or
 * Spring Data types.
 */
public interface LocationPersistencePort {

    /**
     * Insert a newly-created location. Fails on duplicate {@code locationCode}
     * (globally unique — W3) translated by the adapter to
     * {@link com.wms.master.domain.exception.LocationCodeDuplicateException}.
     */
    Location insert(Location newLocation);

    /**
     * Apply mutable-field changes and the current state to the existing row.
     * Bumps the optimistic-lock version. Version mismatch surfaces as
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
     * (translated at the application layer).
     */
    Location update(Location modified);

    Optional<Location> findById(UUID id);

    Optional<Location> findByLocationCode(String locationCode);

    /**
     * Page locations by {@code criteria}, optionally confined to an ABAC
     * data-scope (ADR-MONO-025): when {@code scopeWarehouseCodes} is non-empty the
     * adapter restricts the result (and its count) to locations whose parent
     * warehouse code is in the set, via a DB-side {@code warehouseId IN (subquery
     * on warehouse code)}. {@code null} or empty means unrestricted / unscoped —
     * the net-zero path, no confinement.
     */
    PageResult<Location> findPage(ListLocationsCriteria criteria, PageQuery pageQuery,
                                  Collection<String> scopeWarehouseCodes);
}
