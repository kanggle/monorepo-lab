package com.example.admin.application.tenant;

import java.time.Instant;

/**
 * TASK-BE-343 (ADR-MONO-023 D3): result of a subscription lifecycle mutation
 * delegated to account-service (the entitlement authority). Free of HTTP types.
 *
 * @param previousStatus the prior status, or {@code null} for a brand-new subscribe
 */
public record SubscriptionMutationSummary(
        String tenantId,
        String domainKey,
        String previousStatus,
        String currentStatus,
        Instant occurredAt
) {}
