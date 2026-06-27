package com.example.community.infrastructure.outbox;

import com.example.community.infrastructure.persistence.CommunityOutboxJpaEntity;
import com.example.community.infrastructure.persistence.CommunityOutboxJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit test for {@link OutboxCommunityEventPublisher} (TASK-BE-455 — outbox v1 → v2
 * write adapter). Replaces the v1 {@code CommunityEventPublisherTest} which mocked
 * the lib {@code OutboxWriter}; now we mock the per-service
 * {@link CommunityOutboxJpaRepository}, capture the persisted row, and assert the
 * envelope JSON is the byte-identical 7-field shape the v1
 * {@code BaseEventPublisher.writeEvent} path produced (wire-preservation invariant):
 * {@code eventId == row.id}, {@code source == "community-service"},
 * {@code schemaVersion == 1}, {@code partitionKey == aggregateId}, payload fields
 * verbatim.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("OutboxCommunityEventPublisher 단위 테스트")
class OutboxCommunityEventPublisherTest {

    @Mock
    private CommunityOutboxJpaRepository outboxRepository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private OutboxCommunityEventPublisher publisher() {
        return new OutboxCommunityEventPublisher(outboxRepository, objectMapper,
                Clock.fixed(Instant.parse("2026-04-14T10:00:00Z"), ZoneOffset.UTC));
    }

    private CommunityOutboxJpaEntity captureRow() {
        ArgumentCaptor<CommunityOutboxJpaEntity> captor =
                ArgumentCaptor.forClass(CommunityOutboxJpaEntity.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("publishPostPublished writes the canonical envelope; eventId == row id")
    void publishPostPublished_writesEnvelopeWithPostPayload() throws Exception {
        Instant publishedAt = Instant.parse("2026-04-14T10:00:00Z");

        publisher().publishPostPublished("post-1", "author-1", "GENERAL", "PUBLIC", publishedAt);

        CommunityOutboxJpaEntity row = captureRow();
        assertThat(row.getEventType()).isEqualTo("community.post.published");
        assertThat(row.getAggregateType()).isEqualTo("community");
        assertThat(row.getAggregateId()).isEqualTo("post-1");
        assertThat(row.getPartitionKey()).isEqualTo("post-1");

        JsonNode root = objectMapper.readTree(row.getPayload());
        assertThat(root.get("eventId").asText()).isEqualTo(row.getId().toString());
        assertThat(root.get("eventType").asText()).isEqualTo("community.post.published");
        assertThat(root.get("source").asText()).isEqualTo("community-service");
        assertThat(root.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(root.get("partitionKey").asText()).isEqualTo("post-1");
        assertThat(root.get("occurredAt").asText()).isEqualTo("2026-04-14T10:00:00Z");

        JsonNode payload = root.get("payload");
        assertThat(payload.get("postId").asText()).isEqualTo("post-1");
        assertThat(payload.get("authorAccountId").asText()).isEqualTo("author-1");
        assertThat(payload.get("type").asText()).isEqualTo("GENERAL");
        assertThat(payload.get("visibility").asText()).isEqualTo("PUBLIC");
        assertThat(payload.get("publishedAt").asText()).isEqualTo("2026-04-14T10:00:00Z");
    }

    @Test
    @DisplayName("publishCommentCreated writes envelope partitioned by postId")
    void publishCommentCreated_writesEnvelopePartitionedByPostId() throws Exception {
        Instant createdAt = Instant.parse("2026-04-14T10:05:00Z");

        publisher().publishCommentCreated("comment-1", "post-1", "author-1", "commenter-1", createdAt);

        CommunityOutboxJpaEntity row = captureRow();
        assertThat(row.getAggregateId()).isEqualTo("post-1");

        JsonNode root = objectMapper.readTree(row.getPayload());
        assertThat(root.get("eventType").asText()).isEqualTo("community.comment.created");
        assertThat(root.get("partitionKey").asText()).isEqualTo("post-1");

        JsonNode payload = root.get("payload");
        assertThat(payload.get("commentId").asText()).isEqualTo("comment-1");
        assertThat(payload.get("postId").asText()).isEqualTo("post-1");
        assertThat(payload.get("postAuthorAccountId").asText()).isEqualTo("author-1");
        assertThat(payload.get("commenterAccountId").asText()).isEqualTo("commenter-1");
        assertThat(payload.get("createdAt").asText()).isEqualTo("2026-04-14T10:05:00Z");
    }

    @Test
    @DisplayName("publishReactionAdded new reaction writes isNew=true")
    void publishReactionAdded_newReaction() throws Exception {
        Instant occurredAt = Instant.parse("2026-04-14T10:10:00Z");

        publisher().publishReactionAdded("post-1", "reactor-1", "LIKE", true, occurredAt);

        JsonNode payload = objectMapper.readTree(captureRow().getPayload()).get("payload");
        assertThat(payload.get("postId").asText()).isEqualTo("post-1");
        assertThat(payload.get("reactorAccountId").asText()).isEqualTo("reactor-1");
        assertThat(payload.get("emojiCode").asText()).isEqualTo("LIKE");
        assertThat(payload.get("isNew").asBoolean()).isTrue();
        assertThat(payload.get("occurredAt").asText()).isEqualTo("2026-04-14T10:10:00Z");
    }

    @Test
    @DisplayName("publishReactionAdded changed reaction writes isNew=false")
    void publishReactionAdded_changedReaction() throws Exception {
        publisher().publishReactionAdded("post-2", "reactor-2", "HEART", false,
                Instant.parse("2026-04-14T11:00:00Z"));

        CommunityOutboxJpaEntity row = captureRow();
        assertThat(row.getAggregateId()).isEqualTo("post-2");

        JsonNode payload = objectMapper.readTree(row.getPayload()).get("payload");
        assertThat(payload.get("emojiCode").asText()).isEqualTo("HEART");
        assertThat(payload.get("isNew").asBoolean()).isFalse();
    }
}
