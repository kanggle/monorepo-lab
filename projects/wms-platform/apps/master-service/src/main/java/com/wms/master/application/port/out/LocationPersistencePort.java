package com.wms.master.application.port.out;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.query.ListLocationsCriteria;
import com.wms.master.domain.model.Location;
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

    PageResult<Location> findPage(ListLocationsCriteria criteria, PageQuery pageQuery);
}
