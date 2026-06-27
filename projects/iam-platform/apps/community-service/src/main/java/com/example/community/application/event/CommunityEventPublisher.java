package com.example.community.application.event;

import java.time.Instant;

/**
 * Outbox-based publisher port for community-service Kafka events (TASK-BE-455 —
 * outbox v1 → v2). Previously a concrete {@code extends BaseEventPublisher}; now a
 * port whose v2 implementation is
 * {@link com.example.community.infrastructure.outbox.OutboxCommunityEventPublisher}
 * (the {@code AbstractOutboxPublisher} / {@code OutboxRow} path — ADR-MONO-004 § 5).
 *
 * <p>All events share the standard envelope declared in
 * {@code specs/contracts/events/community-events.md} (eventId, eventType, source,
 * occurredAt, schemaVersion, partitionKey, payload). The wire shape is preserved
 * byte-identically across the v1 → v2 swap (the v1 {@code BaseEventPublisher.writeEvent}
 * 7-field envelope, {@code source = "community-service"}). Kafka key (= partitionKey)
 * is the {@code postId}.
 */
public interface CommunityEventPublisher {

    void publishPostPublished(String postId, String authorAccountId, String type,
                              String visibility, Instant publishedAt);

    void publishCommentCreated(String commentId, String postId,
                               String postAuthorAccountId, String commenterAccountId,
                               Instant createdAt);

    void publishReactionAdded(String postId, String reactorAccountId, String emojiCode,
                              boolean isNew, Instant occurredAt);
}
