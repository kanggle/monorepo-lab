package com.example.erp.approval.infrastructure.persistence.jpa;

import com.example.erp.approval.domain.delegation.DelegationGrant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link DelegationGrant} (TASK-ERP-BE-013). The
 * active-grant lookup filters status + window in SQL/JPQL so the fail-closed
 * resolution does not over-fetch. Every query carries {@code tenantId}
 * (defense-in-depth).
 */
public interface DelegationGrantJpaRepository extends JpaRepository<DelegationGrant, String> {

    Optional<DelegationGrant> findByIdAndTenantId(String id, String tenantId);

    /**
     * The ACTIVE grant {@code delegatorId → delegateId} whose window contains
     * {@code now} (status=ACTIVE ∧ validFrom ≤ now ∧ (validTo is null ∨ validTo ≥
     * now)) AND whose scope covers {@code approvalRequestId} (TASK-ERP-BE-017:
     * scope=GLOBAL OR (scope=REQUEST ∧ scope_request_id = approvalRequestId)).
     * {@code ORDER BY g.scope ASC} makes GLOBAL deterministically precede REQUEST
     * when both match; the impl takes the first (the resolver re-checks
     * {@code isActiveAt} + {@code coversRequest}).
     */
    @Query("SELECT g FROM DelegationGrant g WHERE g.tenantId = :tenantId "
            + "AND g.delegatorId = :delegatorId AND g.delegateId = :delegateId "
            + "AND g.status = com.example.erp.approval.domain.delegation.DelegationStatus.ACTIVE "
            + "AND g.validFrom <= :now "
            + "AND (g.validTo IS NULL OR g.validTo >= :now) "
            + "AND (g.scope = com.example.erp.approval.domain.delegation.DelegationScope.GLOBAL "
            + "  OR (g.scope = com.example.erp.approval.domain.delegation.DelegationScope.REQUEST "
            + "      AND g.scopeRequestId = :approvalRequestId)) "
            + "ORDER BY g.scope ASC, g.createdAt DESC")
    List<DelegationGrant> findActiveGrants(@Param("delegatorId") String delegatorId,
                                           @Param("delegateId") String delegateId,
                                           @Param("tenantId") String tenantId,
                                           @Param("approvalRequestId") String approvalRequestId,
                                           @Param("now") Instant now);

    @Query("SELECT g FROM DelegationGrant g WHERE g.tenantId = :tenantId "
            + "AND (g.delegatorId = :principalId OR g.delegateId = :principalId) "
            + "ORDER BY g.createdAt DESC")
    List<DelegationGrant> findByDelegatorOrDelegate(@Param("principalId") String principalId,
                                                    @Param("tenantId") String tenantId);

    List<DelegationGrant> findByTenantIdAndDelegatorIdOrderByCreatedAtDesc(
            String tenantId, String delegatorId);

    List<DelegationGrant> findByTenantIdAndDelegateIdOrderByCreatedAtDesc(
            String tenantId, String delegateId);
}
