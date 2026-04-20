package com.wms.master.adapter.out.persistence;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.port.out.LotPersistencePort;
import com.wms.master.application.query.ListLotsCriteria;
import com.wms.master.domain.exception.LotNoDuplicateException;
import com.wms.master.domain.model.Lot;
import com.wms.master.domain.model.LotStatus;
import java.time.LocalDate;
import java.util.List;
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
 * Lot persistence adapter. {@code @Repository} is mandatory for Spring's
 * {@code PersistenceExceptionTranslationPostProcessor} to translate Hibernate
 * {@code ConstraintViolationException} into
 * {@code DataIntegrityViolationException}.
 *
 * <p>Mirrors {@code SkuPersistenceAdapter}'s post-BE-009 constraint-name
 * detection: first reads the structured Postgres constraint name via
 * {@link PSQLException#getServerErrorMessage()}, then falls back to a
 * lowercased root-message substring match for H2 (whose driver does not
 * surface a {@link PSQLException} in the cause chain).
 */
@Repository
class LotPersistenceAdapter implements LotPersistencePort {

    private static final String DEFAULT_SORT_FIELD = "updatedAt";
    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;

    private static final String LOT_NO_CONSTRAINT = "uq_lots_sku_lotno";

    private final JpaLotRepository jpaRepository;
    private final LotPersistenceMapper mapper;

    LotPersistenceAdapter(JpaLotRepository jpaRepository, LotPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Lot insert(Lot newLot) {
        LotJpaEntity entity = mapper.toInsertEntity(newLot);
        try {
            LotJpaEntity saved = jpaRepository.saveAndFlush(entity);
            return mapper.toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            throw translateIntegrityViolation(e, newLot);
        }
    }

    @Override
    public Lot update(Lot modified) {
        LotJpaEntity detached = mapper.toNewEntity(modified);
        try {
            LotJpaEntity merged = jpaRepository.saveAndFlush(detached);
            return mapper.toDomain(merged);
        } catch (DataIntegrityViolationException e) {
            throw translateIntegrityViolation(e, modified);
        }
    }

    @Override
    public Optional<Lot> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public PageResult<Lot> findPage(ListLotsCriteria criteria, PageQuery pageQuery) {
        Pageable pageable = toPageable(pageQuery);
        Page<LotJpaEntity> page = jpaRepository.search(
                criteria.skuId(),
                criteria.status(),
                criteria.expiryBefore(),
                criteria.expiryAfter(),
                pageable);

        return new PageResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    @Override
    public List<Lot> findAllByStatusAndExpiryDateBefore(LotStatus status, LocalDate cutoff) {
        return jpaRepository.findAllByStatusAndExpiryDateBefore(status, cutoff).stream()
                .map(mapper::toDomain)
                .toList();
    }

    private RuntimeException translateIntegrityViolation(DataIntegrityViolationException e, Lot lot) {
        // 1) Preferred path: structured Postgres constraint name.
        String constraint = extractPostgresConstraintName(e);
        if (constraint != null) {
            if (LOT_NO_CONSTRAINT.equalsIgnoreCase(constraint)) {
                return new LotNoDuplicateException(lot.getSkuId(), lot.getLotNo());
            }
            // Known machinery fired but unrecognized constraint — surface as 500.
            return e;
        }

        // 2) Fallback: root-message substring match (H2 and drivers that do
        //    not surface a PSQLException in the cause chain).
        String rootMessage = rootMessage(e);
        String lowered = rootMessage == null ? "" : rootMessage.toLowerCase();
        if (lowered.contains(LOT_NO_CONSTRAINT)) {
            return new LotNoDuplicateException(lot.getSkuId(), lot.getLotNo());
        }
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
