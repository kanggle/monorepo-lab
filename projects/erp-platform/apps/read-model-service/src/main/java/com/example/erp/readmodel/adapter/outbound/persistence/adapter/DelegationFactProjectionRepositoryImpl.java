package com.example.erp.readmodel.adapter.outbound.persistence.adapter;

import com.example.erp.readmodel.adapter.outbound.persistence.jpa.DelegationFactProjJpaEntity;
import com.example.erp.readmodel.adapter.outbound.persistence.jpa.DelegationFactProjJpaRepository;
import com.example.erp.readmodel.domain.delegation.DelegationFactProjection;
import com.example.erp.readmodel.domain.delegation.DelegationFactStatus;
import com.example.erp.readmodel.domain.delegation.repository.DelegationFactFilter;
import com.example.erp.readmodel.domain.delegation.repository.DelegationFactProjectionRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JPA adapter for {@link DelegationFactProjectionRepository} (TASK-ERP-BE-015).
 * The list query is a {@link Specification}: the explicit filters (delegatorId /
 * delegateId / status / activeAt) are AND-composed, and the {@code org_scope}
 * read filter is a fail-closed {@code delegatorId IN (scopedDelegatorIds)}
 * predicate (the delegator's department is in the operator's subtree). When
 * {@code scopeUnbounded} the scope predicate is omitted (net-zero).
 *
 * <p>{@code activeAt} is the ACTIVE-at-instant predicate:
 * {@code status = ACTIVE AND valid_from <= activeAt AND (valid_to IS NULL OR
 * activeAt <= valid_to)}.
 */
@Component
@RequiredArgsConstructor
public class DelegationFactProjectionRepositoryImpl implements DelegationFactProjectionRepository {

    private final DelegationFactProjJpaRepository jpa;

    @Override
    public Optional<DelegationFactProjection> findById(String grantId) {
        return jpa.findById(grantId).map(this::toDomain);
    }

    @Override
    public List<DelegationFactProjection> findPage(DelegationFactFilter filter, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "lastEventAt").and(Sort.by("grantId")));
        return jpa.findAll(toSpec(filter), pageable).getContent().stream()
                .map(this::toDomain).toList();
    }

    @Override
    public long count(DelegationFactFilter filter) {
        return jpa.count(toSpec(filter));
    }

    @Override
    public void save(DelegationFactProjection projection) {
        DelegationFactProjJpaEntity e = jpa.findById(projection.grantId())
                .orElseGet(DelegationFactProjJpaEntity::new);
        e.setGrantId(projection.grantId());
        e.setDelegatorId(projection.delegatorId());
        e.setDelegateId(projection.delegateId());
        e.setValidFrom(projection.validFrom());
        e.setValidTo(projection.validTo());
        e.setStatus(projection.status() == null ? null : projection.status().name());
        e.setReason(projection.reason());
        e.setRevokedAt(projection.revokedAt());
        e.setLastEventAt(projection.lastEventAt());
        e.setLastEventId(projection.lastEventId());
        e.setScope(projection.scope());
        e.setScopeRequestId(projection.scopeRequestId());
        jpa.save(e);
    }

    private Specification<DelegationFactProjJpaEntity> toSpec(DelegationFactFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.delegatorId() != null && !filter.delegatorId().isBlank()) {
                predicates.add(cb.equal(root.get("delegatorId"), filter.delegatorId()));
            }
            if (filter.delegateId() != null && !filter.delegateId().isBlank()) {
                predicates.add(cb.equal(root.get("delegateId"), filter.delegateId()));
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status().name()));
            }
            if (filter.activeAt() != null) {
                // status=ACTIVE ∧ validFrom ≤ t ∧ (validTo IS NULL ∨ t ≤ validTo).
                predicates.add(cb.equal(root.get("status"),
                        DelegationFactStatus.ACTIVE.name()));
                predicates.add(cb.lessThanOrEqualTo(root.get("validFrom"), filter.activeAt()));
                predicates.add(cb.or(
                        cb.isNull(root.get("validTo")),
                        cb.greaterThanOrEqualTo(root.get("validTo"), filter.activeAt())));
            }
            if (!filter.scopeUnbounded()) {
                // org_scope read filter (fail-closed): delegator must be in scope.
                if (filter.scopedDelegatorIds().isEmpty()) {
                    predicates.add(cb.disjunction());
                } else {
                    predicates.add(root.get("delegatorId").in(filter.scopedDelegatorIds()));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static int clampSize(int size) {
        if (size <= 0) {
            return 20;
        }
        return Math.min(size, 100);
    }

    private DelegationFactProjection toDomain(DelegationFactProjJpaEntity e) {
        return new DelegationFactProjection(
                e.getGrantId(),
                e.getStatus() == null ? null : DelegationFactStatus.valueOf(e.getStatus()),
                e.getDelegatorId(), e.getDelegateId(),
                e.getValidFrom(), e.getValidTo(), e.getReason(),
                e.getRevokedAt(), e.getLastEventAt(), e.getLastEventId(),
                e.getScope(), e.getScopeRequestId());
    }
}
