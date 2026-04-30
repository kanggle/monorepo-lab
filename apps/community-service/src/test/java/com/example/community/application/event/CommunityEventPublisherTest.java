package com.example.community.application.event;

import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link CommunityEventPublisher} verifying that each publish
 * method writes a canonical envelope to the outbox with the expected payload
 * fields. Mockito-based — does not start the Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
@DisplayName("CommunityEventPublisher 단위 테스트")
class CommunityEventPublisherTest {

    @Mock
    private OutboxWriter outboxWriter;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CommunityEventPublisher publisher;

    @Test
    @DisplayName("publishPostPublished_publicPost_writesEnvelopeWithPostPayload")
    void publishPostPublished_publicPost_writesEnvelopeWithPostPayload() throws Exception {
        Instant publishedAt = Instant.parse("2026-04-14T10:00:00Z");

        publisher.publishPostPublished(
                "post-1",
                "author-1",
                "GENERAL",
                "PUBLIC",
                publishedAt);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(
                eq("community"),
                eq("post-1"),
                eq("community.post.published"),
                jsonCaptor.capture());

        JsonNode root = objectMapper.readTree(jsonCaptor.getValue());
        assertThat(root.get("eventId").asText()).isNotBlank();
        assertThat(root.get("eventType").asText()).isEqualTo("community.post.published");
        assertThat(root.get("source").asText()).isEqualTo("community-service");
        assertThat(root.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(root.get("partitionKey").asText()).isEqualTo("post-1");

        JsonNode payload = root.get("payload");
        assertThat(payload.get("postId").asText()).isEqualTo("post-1");
        assertThat(payload.get("authorAccountId").asText()).isEqualTo("author-1");
        assertThat(payload.get("type").asText()).isEqualTo("GENERAL");
        assertThat(payload.get("visibility").asText()).isEqualTo("PUBLIC");
        assertThat(payload.get("publishedAt").asText()).isEqualTo("2026-04-14T10:00:00Z");
    }

    @Test
    @DisplayName("publishCommentCreated_commentOnPost_writesEnvelopePartitionedByPostId")
    void publishCommentCreated_commentOnPost_writesEnvelopePartitionedByPostId() throws Exception {
        Instant createdAt = Instant.parse("2026-04-14T10:05:00Z");

        publisher.publishCommentCreated(
                "comment-1",
                "post-1",
                "author-1",
                "commenter-1",
                createdAt);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(
                eq("community"),
                eq("post-1"),
                eq("community.comment.created"),
                jsonCaptor.capture());

        JsonNode root = objectMapper.readTree(jsonCaptor.getValue());
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
    @DisplayName("publishReactionAdded_newReaction_writesEnvelopeWithIsNewTrue")
    void publishReactionAdded_newReaction_writesEnvelopeWithIsNewTrue() throws Exception {
        Instant occurredAt = Instant.parse("2026-04-14T10:10:00Z");

        publisher.publishReactionAdded(
                "post-1",
                "reactor-1",
                "LIKE",
                true,
                occurredAt);

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(
                eq("community"),
                eq("post-1"),
                eq("community.reaction.added"),
                jsonCaptor.capture());

        JsonNode payload = objectMapper.readTree(jsonCaptor.getValue()).get("payload");
        assertThat(payload.get("postId").asText()).isEqualTo("post-1");
        assertThat(payload.get("reactorAccountId").asText()).isEqualTo("reactor-1");
        assertThat(payload.get("emojiCode").asText()).isEqualTo("LIKE");
        assertThat(payload.get("isNew").asBoolean()).isTrue();
        assertThat(payload.get("occurredAt").asText()).isEqualTo("2026-04-14T10:10:00Z");
    }

    @Test
    @DisplayName("publishReactionAdded_changedReaction_writesEnvelopeWithIsNewFalse")
    void publishReactionAdded_changedReaction_writesEnvelopeWithIsNewFalse() throws Exception {
        publisher.publishReactionAdded(
                "post-2",
                "reactor-2",
                "HEART",
                false,
                Instant.parse("2026-04-14T11:00:00Z"));

        ArgumentCaptor<String> jsonCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(
                eq("community"),
                eq("post-2"),
                eq("community.reaction.added"),
                jsonCaptor.capture());

        JsonNode payload = objectMapper.readTree(jsonCaptor.getValue()).get("payload");
        assertThat(payload.get("emojiCode").asText()).isEqualTo("HEART");
        assertThat(payload.get("isNew").asBoolean()).isFalse();
    }
}
