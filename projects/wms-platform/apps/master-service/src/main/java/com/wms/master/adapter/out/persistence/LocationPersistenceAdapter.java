package com.wms.master.adapter.out.persistence;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.port.out.LocationPersistencePort;
import com.wms.master.application.query.ListLocationsCriteria;
import com.wms.master.domain.exception.LocationCodeDuplicateException;
import com.wms.master.domain.model.Location;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

/**
 * Location persistence adapter. {@code @Repository} is mandatory — without it,
 * Spring's {@code PersistenceExceptionTranslationPostProcessor} will not
 * translate Hibernate {@code ConstraintViolationException} into
 * {@code DataIntegrityViolationException}, and the global unique-constraint
 * duplicate test would fail.
 *
 * <p>The only unique constraint on this table is
 * {@code uq_locations_location_code} (W3: globally unique), so every
 * integrity violation on insert is treated as a duplicate locationCode. FK
 * violations on {@code warehouse_id} / {@code zone_id} never reach here —
 * the application layer validates those before calling insert.
 */
@Repository
class LocationPersistenceAdapter implements LocationPersistencePort {

    private static final String DEFAULT_SORT_FIELD = "updatedAt";
    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;

    private final JpaLocationRepository jpaRepository;
    private final LocationPersistenceMapper mapper;

    LocationPersistenceAdapter(JpaLocationRepository jpaRepository,
                               LocationPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Location insert(Location newLocation) {
        LocationJpaEntity entity = mapper.toInsertEntity(newLocation);
        try {
            LocationJpaEntity saved = jpaRepository.saveAndFlush(entity);
            return mapper.toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            throw new LocationCodeDuplicateException(newLocation.getLocationCode());
        }
    }

    @Override
    public Location update(Location modified) {
        // No existsById pre-check: the service layer's loadOrThrow already
        // guaranteed existence before entering this adapter. A concurrent
        // delete between load and save surfaces as
        // ObjectOptimisticLockingFailureException / StaleStateException at
        // flush, which the application layer already translates.
        LocationJpaEntity detached = mapper.toNewEntity(modified);
        LocationJpaEntity merged = jpaRepository.saveAndFlush(detached);
        return mapper.toDomain(merged);
    }

    @Override
    public Optional<Location> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Location> findByLocationCode(String locationCode) {
        return jpaRepository.findByLocationCode(locationCode).map(mapper::toDomain);
    }

    @Override
    public PageResult<Location> findPage(ListLocationsCriteria criteria, PageQuery pageQuery) {
        Pageable pageable = toPageable(pageQuery);
        Page<LocationJpaEntity> page = jpaRepository.search(
                criteria.warehouseId(),
                criteria.zoneId(),
                criteria.locationType(),
                criteria.locationCode(),
                criteria.status(),
                pageable);

        return new PageResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
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
