package com.wms.master.adapter.out.persistence;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.port.out.WarehousePersistencePort;
import com.wms.master.application.query.WarehouseListCriteria;
import com.wms.master.domain.exception.WarehouseCodeDuplicateException;
import com.wms.master.domain.model.Warehouse;
import com.wms.master.domain.model.WarehouseStatus;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

/**
 * Warehouse persistence adapter.
 *
 * <p>{@link #hasActiveZonesFor} is backed by {@link JpaZoneRepository}; both
 * adapters share the same datasource so the cross-adapter repository
 * dependency is acceptable — mirrors how {@code ZonePersistenceAdapter}
 * reaches into {@link JpaLocationRepository} for its active-children check.
 */
@Repository
class WarehouseRepositoryImpl implements WarehousePersistencePort {

    private final JpaWarehouseRepository jpaRepository;
    private final JpaZoneRepository jpaZoneRepository;
    private final WarehousePersistenceMapper mapper;

    WarehouseRepositoryImpl(JpaWarehouseRepository jpaRepository,
                                JpaZoneRepository jpaZoneRepository,
                                WarehousePersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.jpaZoneRepository = jpaZoneRepository;
        this.mapper = mapper;
    }

    @Override
    public Warehouse insert(Warehouse newWarehouse) {
        // The insert-path mapper emits version=null so Spring Data JPA treats the
        // entity as new and runs INSERT. A non-null @Version + pre-assigned id
        // otherwise triggers UPDATE semantics and StaleObjectStateException.
        WarehouseJpaEntity entity = mapper.toInsertEntity(newWarehouse);
        try {
            WarehouseJpaEntity saved = jpaRepository.saveAndFlush(entity);
            return mapper.toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            // DB-level unique constraint on warehouse_code is the canonical duplicate guard.
            // Translating every integrity violation to WAREHOUSE_CODE_DUPLICATE is acceptable
            // here because no other unique constraint exists on the warehouses table in v1.
            throw new WarehouseCodeDuplicateException(newWarehouse.getWarehouseCode());
        }
    }

    @Override
    public Warehouse update(Warehouse modified) {
        // No existsById pre-check: the service layer's loadOrThrow already
        // guaranteed existence before entering this adapter. A concurrent
        // delete between load and save surfaces as
        // ObjectOptimisticLockingFailureException / StaleStateException at
        // flush, which the application layer already translates.
        //
        // Build a detached entity carrying the caller's @Version so Hibernate's
        // merge performs the optimistic-lock check at flush. Mismatch surfaces
        // as ObjectOptimisticLockingFailureException (Spring's translation of
        // Hibernate's OptimisticLockException).
        WarehouseJpaEntity detached = mapper.toNewEntity(modified);
        WarehouseJpaEntity merged = jpaRepository.saveAndFlush(detached);
        return mapper.toDomain(merged);
    }

    @Override
    public Optional<Warehouse> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Warehouse> findByCode(String warehouseCode) {
        return jpaRepository.findByWarehouseCode(warehouseCode).map(mapper::toDomain);
    }

    @Override
    public PageResult<Warehouse> findPage(WarehouseListCriteria criteria, PageQuery pageQuery,
                                          Collection<String> scopeWarehouseCodes) {
        Pageable pageable = PageableFactory.from(pageQuery);
        String q = criteria.hasQueryText() ? criteria.q() : null;
        // Net-zero (null/empty scope) routes through the unchanged search() so the
        // golden path is byte-identical; only a deliberately-scoped operator hits
        // the IN-filtered searchScoped() (codes is then guaranteed non-empty).
        Page<WarehouseJpaEntity> page =
                (scopeWarehouseCodes == null || scopeWarehouseCodes.isEmpty())
                        ? jpaRepository.search(criteria.status(), q, pageable)
                        : jpaRepository.searchScoped(criteria.status(), q, scopeWarehouseCodes, pageable);

        return new PageResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Override
    public boolean hasActiveZonesFor(UUID warehouseId) {
        return jpaZoneRepository.existsByWarehouseIdAndStatus(warehouseId, WarehouseStatus.ACTIVE);
    }

}
