package com.example.fanplatform.community.application.event;

import com.example.fanplatform.community.domain.post.PostType;
import com.example.fanplatform.community.domain.post.PostVisibility;
import com.example.fanplatform.community.domain.post.status.PostStatus;
import com.example.fanplatform.community.domain.reaction.ReactionType;
import com.example.messaging.outbox.OutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class CommunityEventPublisherTest {

    @Mock OutboxWriter outboxWriter;

    private CommunityEventPublisher publisher;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        publisher = new CommunityEventPublisher(outboxWriter, objectMapper);
    }

    @Test
    @DisplayName("publishPostPublished → outbox 행 적재 (eventType=community.post.published, partitionKey=postId)")
    void publishPostPublishedAppendsOutboxRow() throws Exception {
        publisher.publishPostPublished(
                "p1", "fan-platform", "author-1",
                PostType.ARTIST_POST, PostVisibility.PUBLIC,
                Instant.parse("2026-05-03T00:00:00Z"));

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(
                eq("community"),
                eq("p1"),
                eq(CommunityEventPublisher.EVENT_POST_PUBLISHED),
                payloadCaptor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = objectMapper.readValue(payloadCaptor.getValue(), Map.class);
        assertThat(envelope).containsKeys("eventId", "eventType", "source",
                "occurredAt", "schemaVersion", "partitionKey", "payload");
        assertThat(envelope.get("eventType")).isEqualTo("community.post.published");
        assertThat(envelope.get("partitionKey")).isEqualTo("p1");
        Map<?, ?> payload = (Map<?, ?>) envelope.get("payload");
        assertThat(payload.get("postId")).isEqualTo("p1");
        assertThat(payload.get("tenantId")).isEqualTo("fan-platform");
        assertThat(payload.get("authorAccountId")).isEqualTo("author-1");
        assertThat(payload.get("visibility")).isEqualTo("PUBLIC");
    }

    @Test
    void publishPostStatusChangedAppendsOutboxRow() {
        publisher.publishPostStatusChanged(
                "p1", "fan-platform",
                PostStatus.PUBLISHED, PostStatus.HIDDEN,
                "operator-1", Instant.now());
        verify(outboxWriter).save(
                eq("community"), eq("p1"),
                eq(CommunityEventPublisher.EVENT_POST_STATUS_CHANGED),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void publishCommentAddedAppendsOutboxRow() {
        publisher.publishCommentAdded(
                "c1", "p1", "fan-platform", "fan-1", Instant.now());
        verify(outboxWriter).save(
                eq("community"), eq("p1"),
                eq(CommunityEventPublisher.EVENT_COMMENT_ADDED),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void publishReactionAddedAppendsOutboxRow() {
        publisher.publishReactionAdded(
                "p1", "fan-platform", "fan-1",
                ReactionType.LIKE, Instant.now());
        verify(outboxWriter).save(
                eq("community"), eq("p1"),
                eq(CommunityEventPublisher.EVENT_REACTION_ADDED),
                org.mockito.ArgumentMatchers.anyString());
    }
}
