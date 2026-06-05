package com.example.erp.approval.infrastructure.persistence.jpa;

import com.example.erp.approval.domain.delegation.DelegationGrant;
import com.example.erp.approval.domain.delegation.DelegationGrantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * JPA adapter for the {@link DelegationGrantRepository} port (TASK-ERP-BE-013).
 * {@code save} uses {@code saveAndFlush} so an optimistic-lock conflict (T5) or
 * constraint violation surfaces inside the use-case Tx (BE-335 lesson). Every
 * query carries {@code tenantId} (defense-in-depth).
 */
@Component
@RequiredArgsConstructor
public class DelegationGrantRepositoryImpl implements DelegationGrantRepository {

    private final DelegationGrantJpaRepository jpa;

    @Override
    public DelegationGrant save(DelegationGrant grant) {
        return jpa.saveAndFlush(grant);
    }

    @Override
    public Optional<DelegationGrant> findById(String id, String tenantId) {
        return jpa.findByIdAndTenantId(id, tenantId);
    }

    @Override
    public Optional<DelegationGrant> findActiveGrant(String delegatorId, String delegateId,
                                                     String tenantId, String approvalRequestId,
                                                     Instant now) {
        return jpa.findActiveGrants(delegatorId, delegateId, tenantId, approvalRequestId, now)
                .stream().findFirst();
    }

    @Override
    public List<DelegationGrant> findByDelegatorOrDelegate(String principalId, String tenantId) {
        return jpa.findByDelegatorOrDelegate(principalId, tenantId);
    }

    @Override
    public List<DelegationGrant> findByDelegator(String delegatorId, String tenantId) {
        return jpa.findByTenantIdAndDelegatorIdOrderByCreatedAtDesc(tenantId, delegatorId);
    }

    @Override
    public List<DelegationGrant> findByDelegate(String delegateId, String tenantId) {
        return jpa.findByTenantIdAndDelegateIdOrderByCreatedAtDesc(tenantId, delegateId);
    }
}
