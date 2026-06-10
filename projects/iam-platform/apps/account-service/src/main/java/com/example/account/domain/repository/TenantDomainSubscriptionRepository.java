package com.example.account.domain.repository;

import com.example.account.domain.tenant.TenantDomainSubscription;

import java.util.List;
import java.util.Optional;

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

    /**
     * TASK-BE-324 (ADR-MONO-019 § 3.3 keystone): returns the ACTIVE subscriptions
     * for a single {@code tenantId} (reverse lookup), ordered identically to
     * {@link #findAllActive()}. Backs the auth-service issuance-time
     * {@code entitled_domains} claim populate (its consumer derives the entitled
     * domainKey list for that tenant).
     */
    List<TenantDomainSubscription> findActiveByTenantId(String tenantId);

    /**
     * TASK-BE-342 (ADR-MONO-023 D3): single subscription by its composite natural
     * key, any status — backs the mutation (transition) path. Empty when the
     * {@code (tenantId, domainKey)} pair has no subscription.
     */
    Optional<TenantDomainSubscription> findByTenantIdAndDomainKey(String tenantId, String domainKey);

    /**
     * TASK-BE-342 (ADR-MONO-023 D3): persist a subscription (create or update).
     * The composite natural key makes this an upsert: a transition on an existing
     * row updates {@code status} + {@code updatedAt} in place.
     */
    TenantDomainSubscription save(TenantDomainSubscription subscription);
}
