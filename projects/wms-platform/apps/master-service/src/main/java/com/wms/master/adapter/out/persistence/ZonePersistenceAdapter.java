package com.wms.master.adapter.out.persistence;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.port.out.ZonePersistencePort;
import com.wms.master.application.query.ListZonesCriteria;
import com.wms.master.domain.exception.ZoneCodeDuplicateException;
import com.wms.master.domain.exception.ZoneNotFoundException;
import com.wms.master.domain.model.Zone;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/**
 * Zone persistence adapter. {@code @Repository} is mandatory — without it,
 * Spring's {@code PersistenceExceptionTranslationPostProcessor} will not
 * translate Hibernate {@code ConstraintViolationException} into
 * {@code DataIntegrityViolationException}, and the compound-unique-constraint
 * duplicate test will fail on H2.
 *
 * <p>In v1 this adapter does NOT implement {@link #hasActiveLocationsFor} for
 * real — Locations ship in TASK-BE-003. Returning {@code false} is an
 * intentional, documented stub.
 */
@Repository
class ZonePersistenceAdapter implements ZonePersistencePort {

    private static final String DEFAULT_SORT_FIELD = "updatedAt";
    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;

    private final JpaZoneRepository jpaRepository;
    private final ZonePersistenceMapper mapper;

    ZonePersistenceAdapter(JpaZoneRepository jpaRepository,
                           ZonePersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Zone insert(Zone newZone) {
        // The insert-path mapper emits version=null so Spring Data JPA treats
        // the entity as new and runs INSERT. A non-null @Version + pre-assigned
        // id otherwise triggers UPDATE semantics and StaleObjectStateException.
        ZoneJpaEntity entity = mapper.toInsertEntity(newZone);
        try {
            ZoneJpaEntity saved = jpaRepository.saveAndFlush(entity);
            return mapper.toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            // The only unique constraint on zones is (warehouse_id, zone_code),
            // so every DataIntegrityViolationException on insert is a duplicate
            // within the same warehouse.
            throw new ZoneCodeDuplicateException(newZone.getWarehouseId(), newZone.getZoneCode());
        }
    }

    @Override
    public Zone update(Zone modified) {
        if (!jpaRepository.existsById(modified.getId())) {
            throw new ZoneNotFoundException(modified.getId().toString());
        }
        // Build a detached entity carrying the caller's @Version so Hibernate's
        // merge performs the optimistic-lock check at flush. Mismatch surfaces
        // as ObjectOptimisticLockingFailureException.
        ZoneJpaEntity detached = mapper.toNewEntity(modified);
        ZoneJpaEntity merged = jpaRepository.saveAndFlush(detached);
        return mapper.toDomain(merged);
    }

    @Override
    public Optional<Zone> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Zone> findByWarehouseIdAndZoneCode(UUID warehouseId, String zoneCode) {
        return jpaRepository.findByWarehouseIdAndZoneCode(warehouseId, zoneCode)
                .map(mapper::toDomain);
    }

    @Override
    public PageResult<Zone> findPage(ListZonesCriteria criteria, PageQuery pageQuery) {
        Pageable pageable = toPageable(pageQuery);
        Page<ZoneJpaEntity> page = jpaRepository.search(
                criteria.warehouseId(),
                criteria.status(),
                criteria.zoneType(),
                pageable);

        return new PageResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Override
    public boolean hasActiveLocationsFor(UUID zoneId) {
        // Locations arrive in TASK-BE-003. Until then no child can exist, so
        // the invariant trivially holds.
        return false;
    }

    private Pageable toPageable(PageQuery pageQuery) {
        Sort sort = resolveSort(pageQuery.sortBy(), pageQuery.sortDirection());
        return PageRequest.of(pageQuery.page(), pageQuery.size(), sort);
    }

    private Sort resolveSort(String sortBy, String sortDirection) {
        String field = (sortBy == null || sortBy.isBlank()) ? DEFAULT_SORT_FIELD : sortBy;
        Sort.Direction direction = parseDirection(sortDirection);
        return Sort.by(direction, field);
    }

    private Sort.Direction parseDirection(String sortDirection) {
        if (sortDirection == null || sortDirection.isBlank()) {
            return DEFAULT_SORT_DIRECTION;
        }
        return "asc".equalsIgnoreCase(sortDirection)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
    }
}
