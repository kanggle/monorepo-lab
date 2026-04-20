package com.wms.master.adapter.out.persistence;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.port.out.SkuPersistencePort;
import com.wms.master.application.query.ListSkusCriteria;
import com.wms.master.domain.exception.BarcodeDuplicateException;
import com.wms.master.domain.exception.SkuCodeDuplicateException;
import com.wms.master.domain.model.Sku;
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
 * SKU persistence adapter. {@code @Repository} is mandatory — without it,
 * Spring's {@code PersistenceExceptionTranslationPostProcessor} will not
 * translate Hibernate {@code ConstraintViolationException} into
 * {@code DataIntegrityViolationException}, and the unique-constraint
 * duplicate tests will fail.
 *
 * <p>Two unique constraints guard inserts — {@code uq_skus_sku_code} and
 * {@code uq_skus_barcode} — so the adapter inspects the caught exception to
 * pick the right domain exception. On Postgres the constraint name is read
 * directly from {@link PSQLException#getServerErrorMessage()} so the path is
 * immune to error-message localization; a lowercased root-message substring
 * match is kept as a fallback for H2 and any driver that does not surface a
 * {@link PSQLException} in the cause chain.
 *
 * <p>{@link #hasActiveLotsFor(UUID)} is stubbed to {@code false} until
 * TASK-BE-006 (Lot aggregate) replaces it with a real
 * {@code JpaLotRepository.existsBySkuIdAndStatus} call.
 */
@Repository
class SkuPersistenceAdapter implements SkuPersistencePort {

    private static final String DEFAULT_SORT_FIELD = "updatedAt";
    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;

    private static final String SKU_CODE_CONSTRAINT = "uq_skus_sku_code";
    private static final String BARCODE_CONSTRAINT = "uq_skus_barcode";

    private final JpaSkuRepository jpaRepository;
    private final SkuPersistenceMapper mapper;

    SkuPersistenceAdapter(JpaSkuRepository jpaRepository, SkuPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Sku insert(Sku newSku) {
        // The insert-path mapper emits version=null so Spring Data JPA treats
        // the entity as new and runs INSERT. A non-null @Version + pre-assigned
        // id otherwise triggers UPDATE semantics and StaleObjectStateException.
        SkuJpaEntity entity = mapper.toInsertEntity(newSku);
        try {
            SkuJpaEntity saved = jpaRepository.saveAndFlush(entity);
            return mapper.toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            throw translateIntegrityViolation(e, newSku);
        }
    }

    @Override
    public Sku update(Sku modified) {
        // No existsById pre-check: the service layer's loadOrThrow already
        // guaranteed existence before entering this adapter. A concurrent
        // delete between load and save surfaces as
        // ObjectOptimisticLockingFailureException / StaleStateException at
        // flush, which the application layer already translates.
        //
        // Build a detached entity carrying the caller's @Version so Hibernate's
        // merge performs the optimistic-lock check at flush.
        SkuJpaEntity detached = mapper.toNewEntity(modified);
        try {
            SkuJpaEntity merged = jpaRepository.saveAndFlush(detached);
            return mapper.toDomain(merged);
        } catch (DataIntegrityViolationException e) {
            throw translateIntegrityViolation(e, modified);
        }
    }

    @Override
    public Optional<Sku> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Sku> findBySkuCode(String skuCodeUpper) {
        return jpaRepository.findBySkuCode(skuCodeUpper).map(mapper::toDomain);
    }

    @Override
    public Optional<Sku> findByBarcode(String barcode) {
        return jpaRepository.findByBarcode(barcode).map(mapper::toDomain);
    }

    @Override
    public PageResult<Sku> findPage(ListSkusCriteria criteria, PageQuery pageQuery) {
        Pageable pageable = toPageable(pageQuery);
        String q = criteria.hasQueryText() ? criteria.q() : null;
        Page<SkuJpaEntity> page = jpaRepository.search(
                criteria.status(),
                criteria.trackingType(),
                criteria.baseUom(),
                criteria.barcode(),
                q,
                pageable);

        return new PageResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Override
    public boolean hasActiveLotsFor(UUID skuId) {
        // Stub per TASK-BE-004 §Lot active-children stub. Replaced by a real
        // JpaLotRepository query in TASK-BE-006.
        return false;
    }

    private RuntimeException translateIntegrityViolation(DataIntegrityViolationException e, Sku sku) {
        // 1) Preferred path: structured Postgres constraint name. Immune to
        //    localized error messages.
        String constraint = extractPostgresConstraintName(e);
        if (constraint != null) {
            if (SKU_CODE_CONSTRAINT.equalsIgnoreCase(constraint)) {
                return new SkuCodeDuplicateException(sku.getSkuCode());
            }
            if (BARCODE_CONSTRAINT.equalsIgnoreCase(constraint)) {
                return new BarcodeDuplicateException(sku.getBarcode());
            }
            // Known constraint machinery fired but the name does not match one
            // we translate — rethrow so the caller sees a 500 rather than a
            // misleading duplicate error.
            return e;
        }

        // 2) Fallback: root-message substring match. Necessary for H2, which
        //    does not produce a PSQLException in the cause chain, and for any
        //    other driver that behaves similarly.
        String rootMessage = rootMessage(e);
        String lowered = rootMessage == null ? "" : rootMessage.toLowerCase();
        if (lowered.contains(SKU_CODE_CONSTRAINT)) {
            return new SkuCodeDuplicateException(sku.getSkuCode());
        }
        if (lowered.contains(BARCODE_CONSTRAINT)) {
            return new BarcodeDuplicateException(sku.getBarcode());
        }
        // Unknown integrity violation — re-throw so it surfaces as 500 rather
        // than a misleading duplicate error.
        return e;
    }

    private static String extractPostgresConstraintName(Throwable t) {
        Throwable cursor = t;
        while (cursor != null) {
            if (cursor instanceof PSQLException pg) {
                if (pg.getServerErrorMessage() != null) {
                    return pg.getServerErrorMessage().getConstraint();
                }
                return null;
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
