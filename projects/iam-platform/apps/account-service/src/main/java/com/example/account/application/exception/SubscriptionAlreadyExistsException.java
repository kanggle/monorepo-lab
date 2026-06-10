package com.example.account.application.exception;

/**
 * TASK-BE-342 (ADR-MONO-023 D3): thrown when {@code subscribe} (create) targets
 * a {@code (tenantId, domainKey)} pair that already has a subscription row.
 * Existing subscriptions are transitioned via PATCH, not re-created. Maps to
 * 409 {@code SUBSCRIPTION_ALREADY_EXISTS}.
 */
public class SubscriptionAlreadyExistsException extends RuntimeException {

    private final String tenantId;
    private final String domainKey;

    public SubscriptionAlreadyExistsException(String tenantId, String domainKey) {
        super("Subscription already exists: (" + tenantId + ", " + domainKey + ")");
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
