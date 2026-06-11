package com.example.fanplatform.notification.application.consumer;

import java.time.Instant;

/**
 * The parsed, validated membership lifecycle event — a pure value object handed
 * to {@code HandleMembershipEventUseCase}. All Kafka / Jackson types stay in the
 * consumer adapter; the application layer receives this command.
 *
 * <p>Activated events carry {@code planMonths} / {@code validFrom} / {@code validTo};
 * canceled events carry {@code reason} (nullable) / {@code canceledAt}. The unused
 * fields are {@code null} for the other event type.
 */
public record MembershipEvent(
        String eventId,
        String eventType,
        String tenantId,
        String accountId,
        String membershipId,
        String tier,
        Integer planMonths,
        Instant validFrom,
        Instant validTo,
        String reason,
        Instant canceledAt) {
}
