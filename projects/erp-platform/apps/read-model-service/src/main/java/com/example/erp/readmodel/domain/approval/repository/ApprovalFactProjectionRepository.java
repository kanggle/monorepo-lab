package com.example.erp.readmodel.domain.approval.repository;

import com.example.erp.readmodel.domain.approval.ApprovalFactProjection;

import java.util.List;
import java.util.Optional;

/**
 * Repository port for {@link ApprovalFactProjection} (Hexagonal — the domain
 * owns the interface; the JPA adapter implements it). Read-only projection store
 * (E5); the only mutation is the consumer-side latest-fact upsert.
 */
public interface ApprovalFactProjectionRepository {

    Optional<ApprovalFactProjection> findById(String approvalRequestId);

    /**
     * Paginated approval-fact page matching the given filter
     * ({@link ApprovalFactFilter}). The {@code org_scope} read filter is applied
     * via the filter's pre-resolved in-scope id sets (the use case resolves the
     * subject → department mapping; the repository only applies the id sets):
     * <ul>
     *   <li>{@code scopeUnbounded == true} → no org_scope narrowing (net-zero).</li>
     *   <li>otherwise only facts whose subject is in scope are returned — a
     *       {@code DEPARTMENT} subject whose id is in {@code scopedDepartmentIds},
     *       or an {@code EMPLOYEE} subject whose id is in
     *       {@code scopedEmployeeSubjectIds}.</li>
     * </ul>
     */
    List<ApprovalFactProjection> findPage(ApprovalFactFilter filter, int page, int size);

    long count(ApprovalFactFilter filter);

    void save(ApprovalFactProjection projection);
}
