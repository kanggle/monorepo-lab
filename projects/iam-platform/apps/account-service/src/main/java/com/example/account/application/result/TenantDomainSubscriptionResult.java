package com.example.account.application.result;

import com.example.account.domain.tenant.TenantDomainSubscription;

/**
 * TASK-BE-322: Application-layer result for a single ACTIVE tenant↔domain
 * subscription. Free of framework/HTTP-layer types; mapped to response DTOs in
 * the presentation layer.
 */
public record TenantDomainSubscriptionResult(
        String tenantId,
        String domainKey
) {
    public static TenantDomainSubscriptionResult from(TenantDomainSubscription s) {
        return new TenantDomainSubscriptionResult(
                s.getTenantId().value(),
                s.getDomainKey()
        );
    }
}
