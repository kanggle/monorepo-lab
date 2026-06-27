package com.example.fanplatform.membership.application.event;

import com.example.fanplatform.membership.domain.membership.MembershipTier;

import java.time.Instant;

/**
 * The ONLY producer path for {@code fan.membership.*} events (port interface,
 * TASK-FAN-BE-020 outbox v2). The implementation
 * ({@code infrastructure.outbox.OutboxMembershipEventPublisher}) appends a
 * {@code membership_outbox} row carrying the canonical 7-field envelope
 * ({@code eventId / eventType / source / occurredAt / schemaVersion / partitionKey
 * / payload}) inside the caller's transaction; {@code MembershipOutboxPublisher}
 * drains it to Kafka.
 *
 * <p>No use case or controller may construct Kafka records directly — every event
 * MUST flow through this port (architecture.md § Boundary rules, AC-8).
 *
 * <p>Topic naming: the envelope's {@code eventType} plus a {@code .v1} suffix
 * (resolved by {@code MembershipOutboxPublisher.topicFor}).
 */
public interface MembershipEventPublisher {

    String EVENT_ACTIVATED = "fan.membership.activated";
    String EVENT_CANCELED = "fan.membership.canceled";
    String EVENT_EXPIRED = "fan.membership.expired";

    /** Emitted on subscribe → ACTIVE (PG mock approved). */
    void publishActivated(String membershipId, String tenantId, String accountId,
                          MembershipTier tier, int planMonths,
                          Instant validFrom, Instant validTo, Instant occurredAt);

    /** Emitted on ACTIVE → CANCELED. A re-cancel of a CANCELED membership emits nothing. */
    void publishCanceled(String membershipId, String tenantId, String accountId,
                         MembershipTier tier, String reason,
                         Instant canceledAt, Instant occurredAt);

    /**
     * Emitted once per membership by the expiry sweeper (TASK-FAN-BE-014) when its
     * window has ended. The membership keeps {@code status=ACTIVE} (read-time
     * expiry — Option B); this is a notification trigger, not a lifecycle change.
     */
    void publishExpired(String membershipId, String tenantId, String accountId,
                        MembershipTier tier, Instant validTo, Instant occurredAt);
}
