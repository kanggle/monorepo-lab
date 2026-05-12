package com.wms.master.adapter.out.persistence;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.wms.master.application.port.out.PartnerPersistencePort;
import com.wms.master.application.query.ListPartnersCriteria;
import com.wms.master.domain.exception.PartnerCodeDuplicateException;
import com.wms.master.domain.model.Partner;
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
 * Partner persistence adapter. {@code @Repository} is mandatory so Spring's
 * {@code PersistenceExceptionTranslationPostProcessor} translates Hibernate
 * {@code ConstraintViolationException} into Spring's
 * {@code DataIntegrityViolationException}.
 *
 * <p>Single unique constraint to guard on insert — {@code uq_partners_partner_code}.
 * Postgres path uses {@link PSQLException#getServerErrorMessage()} for an
 * error-message-agnostic constraint detection; the H2 fallback inspects the
 * lowercased root-message substring.
 */
@Repository
class PartnerPersistenceAdapter implements PartnerPersistencePort {

    private static final String DEFAULT_SORT_FIELD = "updatedAt";
    private static final Sort.Direction DEFAULT_SORT_DIRECTION = Sort.Direction.DESC;

    private static final String PARTNER_CODE_CONSTRAINT = "uq_partners_partner_code";

    private final JpaPartnerRepository jpaRepository;
    private final PartnerPersistenceMapper mapper;

    PartnerPersistenceAdapter(JpaPartnerRepository jpaRepository,
                              PartnerPersistenceMapper mapper) {
        this.jpaRepository = jpaRepository;
        this.mapper = mapper;
    }

    @Override
    public Partner insert(Partner newPartner) {
        PartnerJpaEntity entity = mapper.toInsertEntity(newPartner);
        try {
            PartnerJpaEntity saved = jpaRepository.saveAndFlush(entity);
            return mapper.toDomain(saved);
        } catch (DataIntegrityViolationException e) {
            throw translateIntegrityViolation(e, newPartner);
        }
    }

    @Override
    public Partner update(Partner modified) {
        // Detached entity carrying the caller's @Version so Hibernate's merge
        // performs the optimistic-lock check at flush. The service layer's
        // loadOrThrow already guaranteed existence before entering here.
        PartnerJpaEntity detached = mapper.toNewEntity(modified);
        try {
            PartnerJpaEntity merged = jpaRepository.saveAndFlush(detached);
            return mapper.toDomain(merged);
        } catch (DataIntegrityViolationException e) {
            throw translateIntegrityViolation(e, modified);
        }
    }

    @Override
    public Optional<Partner> findById(UUID id) {
        return jpaRepository.findById(id).map(mapper::toDomain);
    }

    @Override
    public Optional<Partner> findByCode(String partnerCode) {
        return jpaRepository.findByPartnerCode(partnerCode).map(mapper::toDomain);
    }

    @Override
    public boolean existsByCode(String partnerCode) {
        return jpaRepository.existsByPartnerCode(partnerCode);
    }

    @Override
    public PageResult<Partner> findPage(ListPartnersCriteria criteria, PageQuery pageQuery) {
        Pageable pageable = toPageable(pageQuery);
        String q = criteria.hasQueryText() ? criteria.q() : null;
        Page<PartnerJpaEntity> page = jpaRepository.search(
                criteria.status(),
                criteria.partnerType(),
                q,
                pageable);

        return new PageResult<>(
                page.getContent().stream().map(mapper::toDomain).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    private RuntimeException translateIntegrityViolation(DataIntegrityViolationException e, Partner partner) {
        String constraint = extractPostgresConstraintName(e);
        if (constraint != null) {
            if (PARTNER_CODE_CONSTRAINT.equalsIgnoreCase(constraint)) {
                return new PartnerCodeDuplicateException(partner.getPartnerCode());
            }
            return e;
        }

        // Fallback for H2 and any driver that does not surface a PSQLException
        // in the cause chain — root-message substring match.
        String rootMessage = rootMessage(e);
        String lowered = rootMessage == null ? "" : rootMessage.toLowerCase();
        if (lowered.contains(PARTNER_CODE_CONSTRAINT)) {
            return new PartnerCodeDuplicateException(partner.getPartnerCode());
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
