package com.wms.notification.domain.error;

/**
 * No enabled rule for {@code eventType}. Logged at WARN; dedupe row records
 * {@code outcome=NO_RULE}; not necessarily an error in v1 (events not in
 * the seeded routing table simply don't fire alerts).
 */
public final class RoutingRuleNotFoundException extends NotificationDomainException {

    public static final String CODE = "ROUTING_RULE_NOT_FOUND";

    private final String eventType;

    public RoutingRuleNotFoundException(String eventType) {
        super(CODE, "No enabled routing rule for eventType=" + eventType);
        this.eventType = eventType;
    }

    public String eventType() {
        return eventType;
    }
}
