package com.example.fanplatform.community.infrastructure.outbox;

import com.example.common.id.UuidV7;
import com.example.fanplatform.community.application.event.CommunityEventPublisher;
import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.PostStatus;
import com.example.fanplatform.community.domain.reaction.ReactionType;
import com.example.fanplatform.community.infrastructure.jpa.CommunityOutboxJpaEntity;
import com.example.fanplatform.community.infrastructure.jpa.CommunityOutboxJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * community-service outbox write path (TASK-FAN-BE-021, outbox v2).
 *
 * <p>Persists one {@link CommunityOutboxJpaEntity} ({@code community_outbox}
 * table) per domain event inside the caller's transaction, so the business
 * mutation and the outbox row commit atomically. {@code CommunityOutboxPublisher}
 * drains the table to Kafka.
 *
 * <p>Replaces the v1 path ({@code CommunityEventPublisher extends
 * BaseEventPublisher} + lib {@code OutboxWriter} → {@code OutboxJpaEntity},
 * server-assigned {@code BIGSERIAL}, {@code status} string). <b>Wire is preserved
 * exactly</b>: the Kafka record <b>value</b> is the canonical 7-field envelope
 * JSON built in the same field order the lib {@code BaseEventPublisher.writeEvent}
 * used — byte-identical; per-event {@code payload} maps (incl. the {@code base()}
 * helper) copied verbatim; {@code aggregate_id} becomes the Kafka key
 * (partition_key null → relay falls back to aggregateId); the fresh UUIDv7 is
 * both the envelope {@code eventId} and the row PK.
 */
@Component
public class OutboxCommunityEventPublisher implements CommunityEventPublisher {

    private static final String AGGREGATE_TYPE = "community";
    private static final String SOURCE = "fan-platform-community-service";
    private static final int SCHEMA_VERSION = 1;

    private final CommunityOutboxJpaRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public OutboxCommunityEventPublisher(CommunityOutboxJpaRepository outboxRepository,
                                         ObjectMapper objectMapper,
                                         Clock clock) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    public void publishPostPublished(String postId, String tenantId,
                                     String authorAccountId, PostType postType,
                                     PostVisibility visibility, Instant publishedAt) {
        Map<String, Object> payload = base(postId, tenantId);
        payload.put("authorAccountId", authorAccountId);
        payload.put("postType", postType.name());
        payload.put("visibility", visibility.name());
        payload.put("publishedAt", publishedAt.toString());
        writeEvent(AGGREGATE_TYPE, postId, EVENT_POST_PUBLISHED, payload);
    }

    @Override
    public void publishPostStatusChanged(String postId, String tenantId,
                                         PostStatus from, PostStatus to,
                                         String actorAccountId, Instant occurredAt) {
        Map<String, Object> payload = base(postId, tenantId);
        payload.put("from", from.name());
        payload.put("to", to.name());
        payload.put("actorAccountId", actorAccountId);
        payload.put("occurredAt", occurredAt.toString());
        writeEvent(AGGREGATE_TYPE, postId, EVENT_POST_STATUS_CHANGED, payload);
    }

    @Override
    public void publishCommentAdded(String commentId, String postId, String tenantId,
                                    String authorAccountId, Instant occurredAt) {
        Map<String, Object> payload = base(postId, tenantId);
        payload.put("commentId", commentId);
        payload.put("authorAccountId", authorAccountId);
        payload.put("occurredAt", occurredAt.toString());
        writeEvent(AGGREGATE_TYPE, postId, EVENT_COMMENT_ADDED, payload);
    }

    @Override
    public void publishReactionAdded(String postId, String tenantId,
                                     String reactorAccountId, ReactionType reactionType,
                                     Instant occurredAt) {
        Map<String, Object> payload = base(postId, tenantId);
        payload.put("reactorAccountId", reactorAccountId);
        payload.put("reactionType", reactionType.name());
        payload.put("occurredAt", occurredAt.toString());
        writeEvent(AGGREGATE_TYPE, postId, EVENT_REACTION_ADDED, payload);
    }

    private static Map<String, Object> base(String postId, String tenantId) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("postId", postId);
        p.put("tenantId", tenantId);
        return p;
    }

    /**
     * Wrap the payload in the canonical 7-field envelope (v1 shape, same field
     * order as the lib {@code BaseEventPublisher.writeEvent}), serialise to JSON,
     * and persist a pending {@code community_outbox} row in the caller's
     * transaction. The fresh UUIDv7 doubles as the envelope {@code eventId} and
     * the row PK.
     */
    private void writeEvent(String aggregateType, String aggregateId,
                            String eventType, Map<String, Object> payload) {
        UUID eventId = UuidV7.randomUuid();
        Instant occurredAt = Instant.now(clock);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId.toString());
        envelope.put("eventType", eventType);
        envelope.put("source", SOURCE);
        envelope.put("occurredAt", occurredAt.toString());
        envelope.put("schemaVersion", SCHEMA_VERSION);
        envelope.put("partitionKey", aggregateId);
        envelope.put("payload", payload);

        String json;
        try {
            json = objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise " + eventType + " outbox envelope", e);
        }

        outboxRepository.save(new CommunityOutboxJpaEntity(
                eventId, eventType, aggregateType, aggregateId,
                null, // partition_key: publisher falls back to aggregateId (the v1 Kafka key)
                json, occurredAt));
    }
}
