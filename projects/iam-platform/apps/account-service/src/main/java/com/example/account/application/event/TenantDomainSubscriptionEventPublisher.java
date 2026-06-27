package com.example.account.application.event;

import java.time.Instant;

/**
 * Port for the entitlement-plane {@code tenant.subscription.changed} event
 * (TASK-BE-342 / ADR-MONO-023 D4; TASK-BE-451 — outbox v1 → v2).
 *
 * <p>Previously a concrete {@code BaseEventPublisher} subclass writing to the
 * shared lib {@code outbox} via {@code OutboxWriter.saveEvent} (FLAT wire — the
 * payload map serialised as-is, no canonical envelope). It is now a port; the impl
 * {@link com.example.account.infrastructure.outbox.OutboxTenantDomainSubscriptionEventPublisher}
 * reproduces the EXACT v1 flat payload and persists an {@code account_outbox} row
 * (the SAME table as {@code account.*} events) driven by the v2 relay.
 *
 * <p>Emitted on every subscription lifecycle mutation (subscribe / suspend /
 * resume / cancel). Aggregate = the subscription:
 * {@code aggregate_type = "TenantDomainSubscription"},
 * {@code aggregate_id = "<tenantId>:<domainKey>"}.
 */
public interface TenantDomainSubscriptionEventPublisher {

    String EVENT_TYPE = "tenant.subscription.changed";

    /**
     * @param previousStatus the prior status name, or {@code null} for a brand-new
     *                        subscribe (create)
     */
    void publishSubscriptionChanged(String tenantId, String domainKey,
                                    String previousStatus, String currentStatus,
                                    String reason, String actorType, String actorId,
                                    Instant occurredAt);
}
