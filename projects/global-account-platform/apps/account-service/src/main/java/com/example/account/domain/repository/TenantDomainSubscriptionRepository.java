package com.example.account.domain.repository;

import com.example.account.domain.tenant.TenantDomainSubscription;

import java.util.List;

/**
 * TASK-BE-322 (ADR-MONO-019 D2): port for reading tenant↔domain subscriptions.
 *
 * <p>Implemented by {@code TenantDomainSubscriptionRepositoryImpl} in the
 * infrastructure layer. The application layer depends only on this interface —
 * never on JPA specifics. Read-only in step 1 (no save/mutate).
 */
public interface TenantDomainSubscriptionRepository {

    /**
     * Returns every ACTIVE subscription across all tenants/domains, ordered for
     * deterministic projection. The caller (admin-side catalog derivation)
     * groups by {@code domainKey}.
     */
    List<TenantDomainSubscription> findAllActive();
}
