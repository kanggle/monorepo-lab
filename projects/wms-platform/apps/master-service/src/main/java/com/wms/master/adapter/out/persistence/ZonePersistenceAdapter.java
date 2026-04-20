package com.wms.master.adapter.out.persistence;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.port.out.ZonePersistencePort;
import com.wms.master.application.query.ListZonesCriteria;
import com.wms.master.domain.exception.ZoneCodeDuplicateException;
import com.wms.master.domain.model.Zone;
import java.util.Optional;
import java.util.UUID;
import org.postgresql.util.PSQLException;
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
 * <p>{@link #hasActiveLocationsFor} is now backed by a real query against the
 * {@code locations} table (via {@link JpaLocationRepository}) as of
 * TASK-BE-003. Both adapters share the same datasource so the cross-adapter
 * repository dependency is acceptable — see the task's Implementation Notes.
 */
@Repository
class ZonePersistenceAdapter implements ZonePersistencePort {

    private static final String DEFAULT_SORT_FIELD = "updatedAt";
    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;

    private final JpaZoneRepository jpaRepository;
    private final JpaLocationRepository jpaLocationRepository;
    private final ZonePersistenceMapper mapper;

    ZonePersistenceAdapter(JpaZoneRepository jpaRepository,
                           JpaLocationRepository jpaLocationRepository,
                           ZonePersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.jpaLocationRepository = jpaLocationRepository;
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
            // Narrowed to the compound-unique violation only. FK (fk_zones_warehouse)
            // and CHECK (ck_zones_status / ck_zones_zone_type) violations also surface
            // as DataIntegrityViolationException but must NOT be remapped to
            // ZoneCodeDuplicateException — rethrow unchanged so the global handler
            // renders them as 500 rather than a misleading 409.
            if (isZoneCodeUniqueViolation(e)) {
                throw new ZoneCodeDuplicateException(newZone.getWarehouseId(), newZone.getZoneCode());
            }
            throw e;
        }
    }

    @Override
    public Zone update(Zone modified) {
        // No existsById pre-check: the service layer's loadOrThrow already
        // guaranteed existence before entering this adapter. A concurrent
        // delete between load and save surfaces as
        // ObjectOptimisticLockingFailureException / StaleStateException at
        // flush, which the application layer already translates.
        //
        // Build a detached entity carrying the caller's @Version so Hibernate's
        // merge performs the optimistic-lock check at flush. Mismatch surfaces
        // as ObjectOptimisticLockingFailureException.
        ZoneJpaEntity detached = mapper.toNewEntity(modified);
        ZoneJpaEntity merged = jpaRepository.saveAndFlush(detached);
        return mapper.toDomain(merged);
    }

    /**
     * Detects whether the given {@link DataIntegrityViolationException} was
     * caused by a violation of {@code uq_zones_warehouse_code}. Prefers the
     * structured {@link PSQLException#getServerErrorMessage()} constraint
     * field on Postgres (immune to error-message localization); falls back to
     * a message-substring match for H2 and any other driver whose cause chain
     * does not expose a {@link PSQLException}.
     */
    private static boolean isZoneCodeUniqueViolation(DataIntegrityViolationException e) {
        PSQLException pg = findCause(e, PSQLException.class);
        if (pg != null && pg.getServerErrorMessage() != null) {
            String constraint = pg.getServerErrorMessage().getConstraint();
            if (constraint != null) {
                return "uq_zones_warehouse_code".equalsIgnoreCase(constraint);
            }
        }
        String rootMessage = rootMessage(e);
        return rootMessage != null
                && rootMessage.toLowerCase().contains("uq_zones_warehouse_code");
    }

    private static <T extends Throwable> T findCause(Throwable t, Class<T> type) {
        Throwable cursor = t;
        while (cursor != null) {
            if (type.isInstance(cursor)) {
                return type.cast(cursor);
            }
            cursor = cursor.getCause();
        }
        return null;
    }

    private static String rootMessage(Throwable t) {
        Throwable cursor = t;
        Throwable last = t;
        while (cursor != null) {
            last = cursor;
            cursor = cursor.getCause();
        }
        return last == null ? null : last.getMessage();
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
        return jpaLocationRepository.existsByZoneIdAndStatus(
                zoneId, com.wms.master.domain.model.WarehouseStatus.ACTIVE);
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
