package com.example.community.application.event;

import com.example.messaging.event.BaseEventPublisher;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class CommunityEventPublisher extends BaseEventPublisher {

    private static final String AGGREGATE_TYPE = "community";
    private static final String SOURCE = "community-service";

    public CommunityEventPublisher(OutboxWriter outboxWriter, ObjectMapper objectMapper) {
        super(outboxWriter, objectMapper);
    }

    public void publishPostPublished(String postId, String authorAccountId, String type,
                                     String visibility, Instant publishedAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("postId", postId);
        payload.put("authorAccountId", authorAccountId);
        payload.put("type", type);
        payload.put("visibility", visibility);
        payload.put("publishedAt", publishedAt.toString());
        write("community.post.published", postId, payload);
    }

    public void publishCommentCreated(String commentId, String postId,
                                      String postAuthorAccountId, String commenterAccountId,
                                      Instant createdAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("commentId", commentId);
        payload.put("postId", postId);
        payload.put("postAuthorAccountId", postAuthorAccountId);
        payload.put("commenterAccountId", commenterAccountId);
        payload.put("createdAt", createdAt.toString());
        write("community.comment.created", postId, payload);
    }

    public void publishReactionAdded(String postId, String reactorAccountId, String emojiCode,
                                     boolean isNew, Instant occurredAt) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("postId", postId);
        payload.put("reactorAccountId", reactorAccountId);
        payload.put("emojiCode", emojiCode);
        payload.put("isNew", isNew);
        payload.put("occurredAt", occurredAt.toString());
        write("community.reaction.added", postId, payload);
    }

    private void write(String eventType, String aggregateId, Map<String, Object> payload) {
        writeEvent(AGGREGATE_TYPE, aggregateId, eventType, SOURCE, payload);
    }
}
