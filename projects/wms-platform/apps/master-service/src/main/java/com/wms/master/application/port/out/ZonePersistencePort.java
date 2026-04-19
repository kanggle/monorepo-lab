package com.wms.master.application.port.out;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.query.ListZonesCriteria;
import com.wms.master.domain.model.Zone;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for Zone persistence. Implemented by an adapter in
 * {@code adapter.out.persistence}. Uses domain / shared types only — no JPA or
 * Spring Data types.
 *
 * <p>{@link #hasActiveLocationsFor(UUID)} exists so the Zone deactivation path
 * can enforce the "no active Locations" invariant once Locations ship
 * (TASK-BE-003). In v1 the implementation always returns {@code false} because
 * Locations do not yet exist — a documented, 1:1 seam, not speculative
 * generality.
 */
public interface ZonePersistencePort {

    /**
     * Insert a newly-created zone. Fails on duplicate
     * {@code (warehouseId, zoneCode)} translated by the adapter to
     * {@link com.wms.master.domain.exception.ZoneCodeDuplicateException}.
     */
    Zone insert(Zone newZone);

    /**
     * Apply mutable-field changes and the current state to the existing row.
     * Bumps the optimistic-lock version. Version mismatch surfaces as
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
     * (translated at the application layer).
     */
    Zone update(Zone modified);

    Optional<Zone> findById(UUID id);

    Optional<Zone> findByWarehouseIdAndZoneCode(UUID warehouseId, String zoneCode);

    PageResult<Zone> findPage(ListZonesCriteria criteria, PageQuery pageQuery);

    /**
     * Returns whether this zone currently has any ACTIVE child Location. Stubbed
     * to {@code false} in v1 — Locations arrive in TASK-BE-003 and the real
     * implementation will live behind this same port.
     */
    boolean hasActiveLocationsFor(UUID zoneId);
}
