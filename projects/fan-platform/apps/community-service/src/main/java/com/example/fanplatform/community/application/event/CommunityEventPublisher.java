package com.example.fanplatform.community.application.event;

import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.PostStatus;
import com.example.fanplatform.community.domain.reaction.ReactionType;
import com.example.messaging.event.BaseEventPublisher;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Appends {@code community.*} events to the transactional outbox.
 *
 * <p>Uses {@link BaseEventPublisher} from {@code libs:java-messaging} so the
 * envelope shape ({@code eventId / eventType / source / occurredAt /
 * schemaVersion / partitionKey / payload}) is identical across services. The
 * relay (a {@link com.example.messaging.outbox.OutboxPollingScheduler}
 * subclass) hands rows off to Kafka.
 */
@Component
public class CommunityEventPublisher extends BaseEventPublisher {

    private static final String AGGREGATE_TYPE = "community";
    private static final String SOURCE = "fan-platform-community-service";

    public static final String EVENT_POST_PUBLISHED = "community.post.published";
    public static final String EVENT_POST_STATUS_CHANGED = "community.post.status_changed";
    public static final String EVENT_COMMENT_ADDED = "community.comment.added";
    public static final String EVENT_REACTION_ADDED = "community.reaction.added";

    public CommunityEventPublisher(OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        super(outboxWriter, objectMapper);
    }

    public void publishPostPublished(String postId, String tenantId,
                                     String authorAccountId, PostType postType,
                                     PostVisibility visibility, Instant publishedAt) {
        Map<String, Object> payload = base(postId, tenantId);
        payload.put("authorAccountId", authorAccountId);
        payload.put("postType", postType.name());
        payload.put("visibility", visibility.name());
        payload.put("publishedAt", publishedAt.toString());
        writeEvent(AGGREGATE_TYPE, postId, EVENT_POST_PUBLISHED, SOURCE, payload);
    }

    public void publishPostStatusChanged(String postId, String tenantId,
                                         PostStatus from, PostStatus to,
                                         String actorAccountId, Instant occurredAt) {
        Map<String, Object> payload = base(postId, tenantId);
        payload.put("from", from.name());
        payload.put("to", to.name());
        payload.put("actorAccountId", actorAccountId);
        payload.put("occurredAt", occurredAt.toString());
        writeEvent(AGGREGATE_TYPE, postId, EVENT_POST_STATUS_CHANGED, SOURCE, payload);
    }

    public void publishCommentAdded(String commentId, String postId, String tenantId,
                                    String authorAccountId, Instant occurredAt) {
        Map<String, Object> payload = base(postId, tenantId);
        payload.put("commentId", commentId);
        payload.put("authorAccountId", authorAccountId);
        payload.put("occurredAt", occurredAt.toString());
        writeEvent(AGGREGATE_TYPE, postId, EVENT_COMMENT_ADDED, SOURCE, payload);
    }

    public void publishReactionAdded(String postId, String tenantId,
                                     String reactorAccountId, ReactionType reactionType,
                                     Instant occurredAt) {
        Map<String, Object> payload = base(postId, tenantId);
        payload.put("reactorAccountId", reactorAccountId);
        payload.put("reactionType", reactionType.name());
        payload.put("occurredAt", occurredAt.toString());
        writeEvent(AGGREGATE_TYPE, postId, EVENT_REACTION_ADDED, SOURCE, payload);
    }

    private static Map<String, Object> base(String postId, String tenantId) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("postId", postId);
        p.put("tenantId", tenantId);
        return p;
    }
}
