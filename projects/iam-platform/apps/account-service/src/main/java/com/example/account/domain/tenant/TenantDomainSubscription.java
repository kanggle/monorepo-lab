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
 * catalog (ADR-019 D4).
 *
 * <p>The subscription carries its own {@link SubscriptionStatus} lifecycle
 * (ADR-023 D1), distinct from the tenant aggregate's {@link TenantStatus}.
 *
 * <p>{@code domainKey} is a product catalog key, NOT a tenant id.
 */
@Getter
public class TenantDomainSubscription {

    private TenantId tenantId;
    private String domainKey;
    private SubscriptionStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    private TenantDomainSubscription() {
    }

    /**
     * Factory used by infrastructure mappers when reconstituting from persistence.
     */
    public static TenantDomainSubscription reconstitute(TenantId tenantId, String domainKey,
                                                        SubscriptionStatus status,
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
     * TASK-BE-342 (ADR-MONO-023 D1/D3): factory for a brand-new subscription
     * (the {@code subscribe} mutation). A new subscription may only land in a
     * {@link SubscriptionStatus#creatable()} state ({@code PENDING}/{@code ACTIVE}) —
     * never directly {@code SUSPENDED}/{@code CANCELLED}.
     *
     * @throws IllegalArgumentException if {@code status} is not creatable
     */
    public static TenantDomainSubscription create(TenantId tenantId, String domainKey,
                                                  SubscriptionStatus status, Instant now) {
        if (!SubscriptionStatus.creatable().contains(status)) {
            throw new IllegalArgumentException(
                    "A new subscription must be PENDING or ACTIVE, not " + status);
        }
        TenantDomainSubscription s = new TenantDomainSubscription();
        s.tenantId = tenantId;
        s.domainKey = domainKey;
        s.status = status;
        s.createdAt = now;
        s.updatedAt = now;
        return s;
    }

    /**
     * TASK-BE-342 (ADR-MONO-023 D1): apply a lifecycle transition, enforcing the
     * {@link SubscriptionStatus} state machine guard. Mutates {@code status} +
     * {@code updatedAt} and returns the previous status (for the
     * {@code tenant.subscription.changed} event payload).
     *
     * @throws IllegalSubscriptionTransitionException if the transition is illegal
     *         (e.g. {@code CANCELLED → *}, {@code PENDING → SUSPENDED}, or a
     *         self-transition)
     */
    public SubscriptionStatus changeStatus(SubscriptionStatus target, Instant now) {
        if (!this.status.canTransitionTo(target)) {
            throw new IllegalSubscriptionTransitionException(
                    tenantId.value(), domainKey, this.status, target);
        }
        SubscriptionStatus previous = this.status;
        this.status = target;
        this.updatedAt = now;
        return previous;
    }

    /**
     * Returns {@code true} when this subscription's status is
     * {@link SubscriptionStatus#ACTIVE}.
     */
    public boolean isActive() {
        return SubscriptionStatus.ACTIVE == this.status;
    }
}
