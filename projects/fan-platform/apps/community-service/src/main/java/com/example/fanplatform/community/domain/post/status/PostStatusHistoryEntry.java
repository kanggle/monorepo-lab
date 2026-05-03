package com.example.fanplatform.community.domain.post.status;

import java.time.Instant;

/**
 * Immutable audit record for a post status transition.
 * Domain-level representation; infrastructure adapter maps to JPA entity.
 */
public record PostStatusHistoryEntry(
        String postId,
        String tenantId,
        PostStatus fromStatus,
        PostStatus toStatus,
        ActorType actorType,
        String actorId,
        String reason,
        Instant occurredAt
) {
    public static PostStatusHistoryEntry record(String postId,
                                                String tenantId,
                                                PostStatus fromStatus,
                                                PostStatus toStatus,
                                                ActorType actorType,
                                                String actorId,
                                                String reason) {
        return new PostStatusHistoryEntry(postId, tenantId, fromStatus, toStatus,
                actorType, actorId, reason, Instant.now());
    }
}
