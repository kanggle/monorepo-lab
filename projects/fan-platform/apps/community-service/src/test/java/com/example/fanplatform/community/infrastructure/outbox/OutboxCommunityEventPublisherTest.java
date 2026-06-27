package com.example.fanplatform.community.infrastructure.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.example.fanplatform.community.application.event.CommunityEventPublisher;
import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.PostStatus;
import com.example.fanplatform.community.domain.reaction.ReactionType;
import com.example.fanplatform.community.infrastructure.jpa.CommunityOutboxJpaEntity;
import com.example.fanplatform.community.infrastructure.jpa.CommunityOutboxJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit test for the {@link OutboxCommunityEventPublisher} write path
 * (TASK-FAN-BE-021, outbox v2).
 *
 * <p>Asserts each domain event persists a {@code community_outbox} row whose
 * wire-relevant fields are preserved exactly vs the v1 lib
 * {@code BaseEventPublisher.writeEvent}: the canonical 7-field envelope
 * ({@code eventId, eventType, source, occurredAt, schemaVersion=1, partitionKey,
 * payload}) in that field order, the row {@code event_id} reused as the envelope
 * {@code eventId}, {@code aggregate_type}/{@code aggregate_id}/{@code event_type}
 * matching the v1 call, and {@code partition_key} left null so the relay falls
 * back to {@code aggregateId} (the v1 Kafka key).
 */
class OutboxCommunityEventPublisherTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-27T10:15:30Z"), ZoneOffset.UTC);

    private final CommunityOutboxJpaRepository repository = mock(CommunityOutboxJpaRepository.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final OutboxCommunityEventPublisher publisher =
            new OutboxCommunityEventPublisher(repository, objectMapper, CLOCK);

    @Test
    void publishPostPublished_persistsV2Row_withCanonicalEnvelopeAndPreservedKeyFields() throws Exception {
        publisher.publishPostPublished("p1", "fan-platform", "author-1",
                PostType.ARTIST_POST, PostVisibility.PUBLIC,
                Instant.parse("2026-05-03T00:00:00Z"));

        CommunityOutboxJpaEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo(CommunityEventPublisher.EVENT_POST_PUBLISHED);
        assertThat(row.getAggregateType()).isEqualTo("community");
        assertThat(row.getAggregateId()).isEqualTo("p1");
        assertThat(row.getPartitionKey()).isNull();
        assertThat(row.getOccurredAt()).isEqualTo(CLOCK.instant());
        assertThat(row.getPublishedAt()).isNull();

        JsonNode envelope = objectMapper.readTree(row.getPayload());
        assertThat(envelope.get("eventId").asText()).isEqualTo(row.getEventId().toString());
        assertThat(envelope.get("eventType").asText()).isEqualTo(CommunityEventPublisher.EVENT_POST_PUBLISHED);
        assertThat(envelope.get("source").asText()).isEqualTo("fan-platform-community-service");
        assertThat(envelope.get("occurredAt").asText()).isEqualTo(CLOCK.instant().toString());
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(envelope.get("partitionKey").asText()).isEqualTo("p1");
        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("postId").asText()).isEqualTo("p1");
        assertThat(payload.get("tenantId").asText()).isEqualTo("fan-platform");
        assertThat(payload.get("authorAccountId").asText()).isEqualTo("author-1");
        assertThat(payload.get("postType").asText()).isEqualTo("ARTIST_POST");
        assertThat(payload.get("visibility").asText()).isEqualTo("PUBLIC");
    }

    @Test
    void publishPostStatusChanged_persistsV2Row_withFromToPayload() throws Exception {
        publisher.publishPostStatusChanged("p1", "fan-platform",
                PostStatus.PUBLISHED, PostStatus.HIDDEN, "operator-1",
                Instant.parse("2026-05-04T00:00:00Z"));

        CommunityOutboxJpaEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo(CommunityEventPublisher.EVENT_POST_STATUS_CHANGED);
        assertThat(row.getAggregateId()).isEqualTo("p1");

        JsonNode envelope = objectMapper.readTree(row.getPayload());
        assertThat(envelope.get("eventId").asText()).isEqualTo(row.getEventId().toString());
        assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("from").asText()).isEqualTo("PUBLISHED");
        assertThat(payload.get("to").asText()).isEqualTo("HIDDEN");
        assertThat(payload.get("actorAccountId").asText()).isEqualTo("operator-1");
    }

    @Test
    void publishCommentAdded_persistsV2Row_withCommentPayload() throws Exception {
        publisher.publishCommentAdded("c1", "p1", "fan-platform", "fan-1",
                Instant.parse("2026-05-05T00:00:00Z"));

        CommunityOutboxJpaEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo(CommunityEventPublisher.EVENT_COMMENT_ADDED);
        assertThat(row.getAggregateId()).isEqualTo("p1");

        JsonNode envelope = objectMapper.readTree(row.getPayload());
        assertThat(envelope.get("partitionKey").asText()).isEqualTo("p1");
        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("postId").asText()).isEqualTo("p1");
        assertThat(payload.get("commentId").asText()).isEqualTo("c1");
        assertThat(payload.get("authorAccountId").asText()).isEqualTo("fan-1");
    }

    @Test
    void publishReactionAdded_persistsV2Row_withReactionPayload() throws Exception {
        publisher.publishReactionAdded("p1", "fan-platform", "fan-1",
                ReactionType.LIKE, Instant.parse("2026-05-06T00:00:00Z"));

        CommunityOutboxJpaEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo(CommunityEventPublisher.EVENT_REACTION_ADDED);
        assertThat(row.getAggregateId()).isEqualTo("p1");

        JsonNode envelope = objectMapper.readTree(row.getPayload());
        JsonNode payload = envelope.get("payload");
        assertThat(payload.get("reactorAccountId").asText()).isEqualTo("fan-1");
        assertThat(payload.get("reactionType").asText()).isEqualTo("LIKE");
    }

    private CommunityOutboxJpaEntity capturedRow() {
        ArgumentCaptor<CommunityOutboxJpaEntity> captor =
                ArgumentCaptor.forClass(CommunityOutboxJpaEntity.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
