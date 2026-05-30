package com.example.account.domain.tenant;

import lombok.Getter;

import java.time.Instant;

/**
 * TASK-BE-322 (ADR-MONO-019 D2): the entitlement record binding a customer
 * tenant to a federated product/domain.
 *
 * <p>account-service is the source of truth for which tenants are entitled to
 * which domains ({@code gap}/{@code wms}/{@code scm}/{@code erp}/{@code finance}).
 * admin-service projects ACTIVE subscriptions into the platform-console product
 * catalog (ADR-019 D4); this domain object is read-only from account-service's
 * perspective in step 1 (no mutation surface — subscription management is a
 * later step).
 *
 * <p>{@code domainKey} is a product catalog key, NOT a tenant id.
 */
@Getter
public class TenantDomainSubscription {

    private TenantId tenantId;
    private String domainKey;
    private TenantStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    private TenantDomainSubscription() {
    }

    /**
     * Factory used by infrastructure mappers when reconstituting from persistence.
     */
    public static TenantDomainSubscription reconstitute(TenantId tenantId, String domainKey,
                                                        TenantStatus status,
                                                        Instant createdAt, Instant updatedAt) {
        TenantDomainSubscription s = new TenantDomainSubscription();
        s.tenantId = tenantId;
        s.domainKey = domainKey;
        s.status = status;
        s.createdAt = createdAt;
        s.updatedAt = updatedAt;
        return s;
    }

    /**
     * Returns {@code true} when this subscription's status is
     * {@link TenantStatus#ACTIVE}.
     */
    public boolean isActive() {
        return TenantStatus.ACTIVE == this.status;
    }
}
