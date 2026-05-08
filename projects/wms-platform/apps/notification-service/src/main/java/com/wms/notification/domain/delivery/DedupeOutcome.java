package com.wms.notification.domain.delivery;

/**
 * Outcome recorded in {@code notification_event_dedupe.outcome} so replays
 * surface the original classification.
 */
public enum DedupeOutcome {

    /** Event matched a routing rule and one or more delivery rows were created. */
    QUEUED,
    /** Event matched the routing rule's eventType but the matcher predicate filtered it out. */
    FILTERED,
    /** No enabled routing rule for the event type — observability only, not an error. */
    NO_RULE,
    /** Routing or persistence step raised a domain error (e.g. ROUTING_AMBIGUOUS). */
    ERROR
}
