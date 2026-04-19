package com.wms.master.adapter.out.persistence;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.port.out.WarehousePersistencePort;
import com.wms.master.application.query.WarehouseListCriteria;
import com.wms.master.domain.exception.WarehouseCodeDuplicateException;
import com.wms.master.domain.exception.WarehouseNotFoundException;
import com.wms.master.domain.model.Warehouse;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

@Repository
class WarehousePersistenceAdapter implements WarehousePersistencePort {

    private static final String DEFAULT_SORT_FIELD = "updatedAt";
    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;

    private final JpaWarehouseRepository jpaRepository;
    private final WarehousePersistenceMapper mapper;

    WarehousePersistenceAdapter(JpaWarehouseRepository jpaRepository,
                                WarehousePersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
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
        if (!jpaRepository.existsById(modified.getId())) {
            throw new WarehouseNotFoundException(modified.getId().toString());
        }
        // Build a detached entity carrying the caller's @Version so Hibernate's merge
        // performs the optimistic-lock check at flush. Mismatch surfaces as
        // ObjectOptimisticLockingFailureException (Spring's translation of
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
    public PageResult<Warehouse> findPage(WarehouseListCriteria criteria, PageQuery pageQuery) {
        Pageable pageable = toPageable(pageQuery);
        String q = criteria.hasQueryText() ? criteria.q() : null;
        Page<WarehouseJpaEntity> page = jpaRepository.search(criteria.status(), q, pageable);

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
