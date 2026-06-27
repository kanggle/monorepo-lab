package com.example.community.infrastructure.outbox;

import com.example.common.id.UuidV7;
import com.example.community.application.event.CommunityEventPublisher;
import com.example.community.infrastructure.persistence.CommunityOutboxJpaEntity;
import com.example.community.infrastructure.persistence.CommunityOutboxJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * {@link CommunityEventPublisher} implementation (TASK-BE-455 — outbox v1 → v2).
 *
 * <p>Builds the canonical event envelope and persists a {@code community_outbox}
 * row in the caller's transaction (the {@code AbstractOutboxPublisher} /
 * {@code OutboxRow} path — ADR-MONO-004 § 5, mirroring the in-worktree auth-service's
 * {@code OutboxAuthEventPublisher} + finance account-service's
 * {@code OutboxAccountEventPublisher}). The {@code CommunityOutboxPublisher} relay
 * forwards the row to Kafka asynchronously; downstream consumers dedupe on the
 * envelope {@code eventId} (at-least-once).
 *
 * <p><b>Wire-shape preserved.</b> The envelope is the EXACT 7-field shape the
 * previous {@code BaseEventPublisher.writeEvent} path emitted —
 * {@code {eventId, eventType, source, occurredAt, schemaVersion, partitionKey,
 * payload}}, {@code source = "community-service"}, every payload field/order
 * unchanged — so consumers are unaffected. Each publish method's payload-Map
 * construction is copied VERBATIM from the v1 {@code CommunityEventPublisher}. The
 * only change: the envelope {@code eventId} now equals the {@code community_outbox}
 * PK (both UUIDv7) so the Kafka {@code eventId} header matches the payload.
 */
@Component
public class OutboxCommunityEventPublisher implements CommunityEventPublisher {

    private static final String AGGREGATE_TYPE = "community";
    private static final String SOURCE = "community-service";
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

    @Override
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

    @Override
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
        writeEvent(AGGREGATE_TYPE, aggregateId, eventType, payload);
    }

    /**
     * Wrap {@code payload} in the canonical 7-field envelope (preserved from the
     * v1 {@code BaseEventPublisher.writeEvent} path), serialise it, and persist a
     * pending {@code community_outbox} row in the caller's transaction. The generated
     * {@link UuidV7} doubles as the envelope {@code eventId} and the row PK;
     * {@code partition_key = aggregateId} (the v1 Kafka key).
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
            throw new IllegalStateException(
                    "failed to serialise " + eventType + " outbox envelope", e);
        }

        outboxRepository.save(CommunityOutboxJpaEntity.create(
                eventId, aggregateType, aggregateId, eventType,
                json, aggregateId, occurredAt));
    }
}
