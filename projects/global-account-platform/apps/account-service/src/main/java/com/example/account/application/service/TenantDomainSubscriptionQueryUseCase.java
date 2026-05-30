package com.example.account.application.service;

import com.example.account.application.result.TenantDomainSubscriptionResult;
import com.example.account.domain.repository.TenantDomainSubscriptionRepository;
import com.example.account.domain.tenant.TenantDomainSubscription;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TASK-BE-322 (ADR-MONO-019 D2): read-only query exposing ACTIVE tenant↔domain
 * subscriptions for the admin-service catalog projection (ADR-019 D4).
 *
 * <p>Read-only — no audit row (consistent with
 * {@code TenantAccountQueryUseCase} / {@code ListTenantsUseCase} read-path
 * policy; subscription management is a later step).
 */
@Service
@RequiredArgsConstructor
public class TenantDomainSubscriptionQueryUseCase {

    private final TenantDomainSubscriptionRepository subscriptionRepository;

    /**
     * Returns every ACTIVE subscription, optionally filtered to a single
     * {@code domainKey} ({@code null}/blank = no filter).
     */
    @Transactional(readOnly = true)
    public List<TenantDomainSubscriptionResult> listActive(String domainKey) {
        List<TenantDomainSubscription> subscriptions = subscriptionRepository.findAllActive();
        return subscriptions.stream()
                .filter(s -> domainKey == null || domainKey.isBlank()
                        || domainKey.equals(s.getDomainKey()))
                .map(TenantDomainSubscriptionResult::from)
                .toList();
    }
}
