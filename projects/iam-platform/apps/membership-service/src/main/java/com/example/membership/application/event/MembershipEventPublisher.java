package com.example.membership.application.event;

import com.example.membership.domain.subscription.Subscription;

/**
 * Outbox-based publisher port for membership-service Kafka events (TASK-BE-454 —
 * outbox v1 → v2). Previously a concrete {@code extends BaseEventPublisher}; now a
 * port whose v2 implementation is
 * {@link com.example.membership.infrastructure.outbox.OutboxMembershipEventPublisher}
 * (the {@code AbstractOutboxPublisher} / {@code OutboxRow} path — ADR-MONO-004 § 5).
 *
 * <p>Each event uses the domain factory ({@link Subscription#buildActivatedEvent()}
 * etc.) for its {@code eventType()} + {@code payload()}, wrapped in the standard
 * envelope (eventId, eventType, source="membership-service", occurredAt,
 * schemaVersion, partitionKey, payload). The wire shape is preserved byte-identically
 * across the v1 → v2 swap (the v1 {@code BaseEventPublisher.writeEvent} 7-field
 * envelope). Kafka key (= partitionKey) is the subscription's {@code accountId}.
 */
public interface MembershipEventPublisher {

    void publishActivated(Subscription s);

    void publishExpired(Subscription s);

    void publishCancelled(Subscription s);
}
