package com.wms.master.application.port.out;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.query.WarehouseListCriteria;
import com.wms.master.domain.model.Warehouse;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for Warehouse persistence. Implemented by an adapter in
 * {@code adapter.out.persistence}. Uses domain / shared types only — no
 * JPA or Spring Data types.
 *
 * <p>Split {@code insert} vs {@code update} so the adapter can pick the
 * correct strategy (persist vs merge + dirty-check) based on caller intent
 * instead of inferring it from domain state.
 *
 * <p>{@link #hasActiveZonesFor(UUID)} exists so the Warehouse deactivation
 * path can enforce the "no active child Zones" invariant. Backed by a real
 * query against the {@code zones} table — the adapter implementation lives
 * alongside the Warehouse adapter and shares the same datasource.
 */
public interface WarehousePersistencePort {

    /**
     * Insert a newly-created warehouse. Fails on duplicate {@code warehouseCode}
     * (translated by the adapter to
     * {@link com.wms.master.domain.exception.WarehouseCodeDuplicateException}).
     */
    Warehouse insert(Warehouse newWarehouse);

    /**
     * Apply mutable-field changes and the current state to the existing row.
     * Bumps the optimistic-lock version. Fails with
     * {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
     * (translated at the application layer) when the supplied aggregate's
     * version does not match the stored version.
     */
    Warehouse update(Warehouse modified);

    Optional<Warehouse> findById(UUID id);

    Optional<Warehouse> findByCode(String warehouseCode);

    PageResult<Warehouse> findPage(WarehouseListCriteria criteria, PageQuery pageQuery);

    /**
     * Returns whether this warehouse currently has any ACTIVE child Zone.
     * Implemented as a real query against the {@code zones} table; the
     * Warehouse deactivation path in the service layer relies on this to
     * enforce the "no active child zones" invariant — mirrors
     * {@link com.wms.master.application.port.out.ZonePersistencePort#hasActiveLocationsFor(UUID)}.
     */
    boolean hasActiveZonesFor(UUID warehouseId);
}
