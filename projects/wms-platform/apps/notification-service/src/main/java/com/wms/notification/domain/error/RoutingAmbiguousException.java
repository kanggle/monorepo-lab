package com.wms.notification.domain.error;

import java.util.List;
import java.util.UUID;

/**
 * Multiple enabled rules matched the same {@code eventType}. Storage-level
 * UNIQUE partial index normally prevents this; surfaces only on bad manual
 * DB edit (or two-rule race during admin v2 CRUD).
 */
public final class RoutingAmbiguousException extends NotificationDomainException {

    public static final String CODE = "ROUTING_AMBIGUOUS";

    private final String eventType;
    private final List<UUID> matchedRuleIds;

    public RoutingAmbiguousException(String eventType, List<UUID> matchedRuleIds) {
        super(CODE, "Multiple enabled rules matched eventType=" + eventType
                + " ruleIds=" + matchedRuleIds);
        this.eventType = eventType;
        this.matchedRuleIds = List.copyOf(matchedRuleIds);
    }

    public String eventType() {
        return eventType;
    }

    public List<UUID> matchedRuleIds() {
        return matchedRuleIds;
    }
}
