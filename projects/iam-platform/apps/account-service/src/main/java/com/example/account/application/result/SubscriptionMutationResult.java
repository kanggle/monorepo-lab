package com.example.account.application.result;

import com.example.account.domain.tenant.SubscriptionStatus;

import java.time.Instant;

/**
 * TASK-BE-342 (ADR-MONO-023 D3): result of a subscription lifecycle mutation
 * (subscribe / suspend / resume / cancel).
 *
 * @param previousStatus the prior status, or {@code null} for a brand-new
 *                       subscribe (create)
 */
public record SubscriptionMutationResult(
        String tenantId,
        String domainKey,
        SubscriptionStatus previousStatus,
        SubscriptionStatus currentStatus,
        Instant occurredAt
) {}
