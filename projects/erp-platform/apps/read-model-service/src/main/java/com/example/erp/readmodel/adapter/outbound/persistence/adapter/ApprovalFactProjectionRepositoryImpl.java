package com.example.erp.readmodel.adapter.outbound.persistence.adapter;

import com.example.erp.readmodel.adapter.outbound.persistence.jpa.ApprovalFactProjJpaEntity;
import com.example.erp.readmodel.adapter.outbound.persistence.jpa.ApprovalFactProjJpaRepository;
import com.example.erp.readmodel.domain.approval.ApprovalFactProjection;
import com.example.erp.readmodel.domain.approval.ApprovalStatus;
import com.example.erp.readmodel.domain.approval.ApprovalSubjectType;
import com.example.erp.readmodel.domain.approval.repository.ApprovalFactFilter;
import com.example.erp.readmodel.domain.approval.repository.ApprovalFactProjectionRepository;
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
 * JPA adapter for {@link ApprovalFactProjectionRepository}. The list query is a
 * {@link Specification}: the explicit filters (status / subjectType / subjectId /
 * approverId / submitterId) are AND-composed, and the {@code org_scope} read
 * filter is a fail-closed disjunction over the pre-resolved in-scope id sets
 * (a {@code DEPARTMENT} subject in {@code scopedDepartmentIds} OR an
 * {@code EMPLOYEE} subject in {@code scopedEmployeeSubjectIds}). When
 * {@code scopeUnbounded} the scope predicate is omitted (net-zero).
 */
@Component
@RequiredArgsConstructor
public class ApprovalFactProjectionRepositoryImpl implements ApprovalFactProjectionRepository {

    private final ApprovalFactProjJpaRepository jpa;

    @Override
    public Optional<ApprovalFactProjection> findById(String approvalRequestId) {
        return jpa.findById(approvalRequestId).map(this::toDomain);
    }

    @Override
    public List<ApprovalFactProjection> findPage(ApprovalFactFilter filter, int page, int size) {
        Pageable pageable = PageRequest.of(Math.max(page, 0), clampSize(size),
                Sort.by(Sort.Direction.DESC, "lastEventAt").and(Sort.by("approvalRequestId")));
        return jpa.findAll(toSpec(filter), pageable).getContent().stream()
                .map(this::toDomain).toList();
    }

    @Override
    public long count(ApprovalFactFilter filter) {
        return jpa.count(toSpec(filter));
    }

    @Override
    public void save(ApprovalFactProjection projection) {
        ApprovalFactProjJpaEntity e = jpa.findById(projection.approvalRequestId())
                .orElseGet(ApprovalFactProjJpaEntity::new);
        e.setApprovalRequestId(projection.approvalRequestId());
        e.setStatus(projection.status() == null ? null : projection.status().name());
        e.setSubjectType(projection.subjectType() == null ? null : projection.subjectType().name());
        e.setSubjectId(projection.subjectId());
        e.setApproverId(projection.approverId());
        e.setSubmitterId(projection.submitterId());
        e.setSubmittedAt(projection.submittedAt());
        e.setFinalizedAt(projection.finalizedAt());
        e.setLastReason(projection.lastReason());
        e.setLastEventAt(projection.lastEventAt());
        e.setLastEventId(projection.lastEventId());
        jpa.save(e);
    }

    private Specification<ApprovalFactProjJpaEntity> toSpec(ApprovalFactFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("status"), filter.status().name()));
            }
            if (filter.subjectType() != null) {
                predicates.add(cb.equal(root.get("subjectType"), filter.subjectType().name()));
            }
            if (filter.subjectId() != null && !filter.subjectId().isBlank()) {
                predicates.add(cb.equal(root.get("subjectId"), filter.subjectId()));
            }
            if (filter.approverId() != null && !filter.approverId().isBlank()) {
                predicates.add(cb.equal(root.get("approverId"), filter.approverId()));
            }
            if (filter.submitterId() != null && !filter.submitterId().isBlank()) {
                predicates.add(cb.equal(root.get("submitterId"), filter.submitterId()));
            }
            if (!filter.scopeUnbounded()) {
                // org_scope read filter (fail-closed): subject must be in scope.
                List<Predicate> scopeOr = new ArrayList<>();
                if (!filter.scopedDepartmentIds().isEmpty()) {
                    scopeOr.add(cb.and(
                            cb.equal(root.get("subjectType"),
                                    ApprovalSubjectType.DEPARTMENT.name()),
                            root.get("subjectId").in(filter.scopedDepartmentIds())));
                }
                if (!filter.scopedEmployeeSubjectIds().isEmpty()) {
                    scopeOr.add(cb.and(
                            cb.equal(root.get("subjectType"),
                                    ApprovalSubjectType.EMPLOYEE.name()),
                            root.get("subjectId").in(filter.scopedEmployeeSubjectIds())));
                }
                // Empty scope (no in-scope departments/employees) → match nothing.
                predicates.add(scopeOr.isEmpty() ? cb.disjunction()
                        : cb.or(scopeOr.toArray(new Predicate[0])));
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

    private ApprovalFactProjection toDomain(ApprovalFactProjJpaEntity e) {
        return new ApprovalFactProjection(
                e.getApprovalRequestId(),
                e.getStatus() == null ? null : ApprovalStatus.valueOf(e.getStatus()),
                e.getSubjectType() == null ? null
                        : ApprovalSubjectType.valueOf(e.getSubjectType()),
                e.getSubjectId(), e.getApproverId(), e.getSubmitterId(),
                e.getSubmittedAt(), e.getFinalizedAt(), e.getLastReason(),
                e.getLastEventAt(), e.getLastEventId());
    }
}
