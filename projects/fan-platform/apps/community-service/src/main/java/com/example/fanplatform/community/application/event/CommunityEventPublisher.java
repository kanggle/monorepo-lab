package com.example.fanplatform.community.application.event;

import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.PostStatus;
import com.example.fanplatform.community.domain.reaction.ReactionType;

import java.time.Instant;

/**
 * The producer port for {@code community.*} events (port interface,
 * TASK-FAN-BE-021 outbox v2). The implementation
 * ({@code infrastructure.outbox.OutboxCommunityEventPublisher}) appends a
 * {@code community_outbox} row carrying the canonical 7-field envelope
 * ({@code eventId / eventType / source / occurredAt / schemaVersion / partitionKey
 * / payload}) inside the caller's transaction; {@code CommunityOutboxPublisher}
 * drains it to Kafka.
 *
 * <p><strong>Topic naming convention</strong>: every Kafka topic name is the
 * envelope's {@code eventType} field plus a {@code .v1} suffix (resolved by
 * {@code CommunityOutboxPublisher.topicFor}). Example: the envelope
 * {@code eventType="community.post.published"} is published on the topic
 * {@code community.post.published.v1}. Consumers MUST subscribe to the suffixed
 * topic name; the envelope's {@code eventType} stays unsuffixed for forward
 * compatibility. See {@code platform/event-driven-policy.md} and
 * {@code projects/fan-platform/specs/contracts/events/community-events.md}
 * § "Common envelope".
 */
public interface CommunityEventPublisher {

    String EVENT_POST_PUBLISHED = "community.post.published";
    String EVENT_POST_STATUS_CHANGED = "community.post.status_changed";
    String EVENT_COMMENT_ADDED = "community.comment.added";
    String EVENT_REACTION_ADDED = "community.reaction.added";

    void publishPostPublished(String postId, String tenantId,
                              String authorAccountId, PostType postType,
                              PostVisibility visibility, Instant publishedAt);

    void publishPostStatusChanged(String postId, String tenantId,
                                  PostStatus from, PostStatus to,
                                  String actorAccountId, Instant occurredAt);

    void publishCommentAdded(String commentId, String postId, String tenantId,
                             String authorAccountId, Instant occurredAt);

    void publishReactionAdded(String postId, String tenantId,
                              String reactorAccountId, ReactionType reactionType,
                              Instant occurredAt);
}
