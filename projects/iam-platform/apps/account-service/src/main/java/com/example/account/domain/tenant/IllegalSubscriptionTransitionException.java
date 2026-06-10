package com.example.account.domain.tenant;

/**
 * TASK-BE-341/342 (ADR-MONO-023 D1): thrown when a tenant↔domain subscription
 * status transition violates the {@link SubscriptionStatus} state machine
 * (e.g. {@code CANCELLED → ACTIVE}, {@code PENDING → SUSPENDED}, or a
 * self-transition). Maps to 409 {@code SUBSCRIPTION_TRANSITION_INVALID}.
 */
public class IllegalSubscriptionTransitionException extends RuntimeException {

    private final String tenantId;
    private final String domainKey;
    private final SubscriptionStatus from;
    private final SubscriptionStatus to;

    public IllegalSubscriptionTransitionException(String tenantId, String domainKey,
                                                  SubscriptionStatus from, SubscriptionStatus to) {
        super("Illegal subscription transition for (" + tenantId + ", " + domainKey + "): "
                + from + " → " + to);
        this.tenantId = tenantId;
        this.domainKey = domainKey;
        this.from = from;
        this.to = to;
    }

    public String getTenantId() {
        return tenantId;
    }

    public String getDomainKey() {
        return domainKey;
    }

    public SubscriptionStatus getFrom() {
        return from;
    }

    public SubscriptionStatus getTo() {
        return to;
    }
}
