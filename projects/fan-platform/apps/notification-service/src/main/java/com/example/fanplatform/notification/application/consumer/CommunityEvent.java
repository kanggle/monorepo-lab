package com.example.fanplatform.notification.application.consumer;

import java.time.Instant;
import java.util.List;

/**
 * The parsed, validated community interaction event — a pure value object handed
 * to {@code HandleCommunityEventUseCase} (TASK-FAN-BE-026). All Kafka / Jackson
 * types stay in the consumer adapter; the application layer receives this command.
 *
 * <p>Shape per {@code specs/contracts/events/community-events.md}:
 * <ul>
 *   <li>{@code community.comment.added} carries {@code commentId} and
 *       {@code mentionedAccountIds}; {@code reactionType} is {@code null}.</li>
 *   <li>{@code community.reaction.added} carries {@code reactionType};
 *       {@code commentId} is {@code null} and {@code mentionedAccountIds} empty.</li>
 * </ul>
 *
 * @param actorAccountId       who performed the interaction — the comment's author
 *                             ({@code authorAccountId}) or the reactor
 *                             ({@code reactorAccountId})
 * @param postAuthorAccountId  the alert recipient for REPLY / REACTION_BADGE.
 *                             <b>Nullable</b>: an event emitted before the
 *                             TASK-FAN-BE-026 producer enrichment has no such
 *                             field, and that is a rollout artifact, not a
 *                             malformed event — the use case skips (logs +
 *                             dedupes) rather than routing to the DLQ.
 * @param mentionedAccountIds  mention recipients; never {@code null} (an absent
 *                             key parses to an empty list), possibly empty
 */
public record CommunityEvent(
        String eventId,
        String eventType,
        String tenantId,
        String postId,
        String commentId,
        String actorAccountId,
        String postAuthorAccountId,
        List<String> mentionedAccountIds,
        String reactionType,
        Instant occurredAt) {

    /**
     * Normalises {@code mentionedAccountIds} to a non-null immutable list so every
     * downstream iteration is safe regardless of how the record was constructed
     * (the parser already yields an empty list for an absent key; this makes the
     * invariant structural rather than conventional).
     */
    public CommunityEvent {
        mentionedAccountIds = mentionedAccountIds == null
                ? List.of()
                : List.copyOf(mentionedAccountIds);
    }
}
