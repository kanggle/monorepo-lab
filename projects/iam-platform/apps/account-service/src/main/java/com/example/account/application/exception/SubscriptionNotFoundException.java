package com.example.account.application.exception;

/**
 * TASK-BE-342 (ADR-MONO-023 D3): thrown when a subscription status transition
 * targets a {@code (tenantId, domainKey)} pair that has no subscription row.
 * Maps to 404 {@code SUBSCRIPTION_NOT_FOUND}.
 */
public class SubscriptionNotFoundException extends RuntimeException {

    private final String tenantId;
    private final String domainKey;

    public SubscriptionNotFoundException(String tenantId, String domainKey) {
        super("Subscription not found: (" + tenantId + ", " + domainKey + ")");
        this.tenantId = tenantId;
        this.domainKey = domainKey;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getDomainKey() {
        return domainKey;
    }
}
