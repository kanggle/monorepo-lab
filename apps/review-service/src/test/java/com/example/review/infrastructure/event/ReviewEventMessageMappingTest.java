package com.example.review.infrastructure.event;

import com.example.review.domain.event.ReviewCreatedPayload;
import com.example.review.domain.event.ReviewDeletedPayload;
import com.example.review.domain.event.ReviewEvent;
import com.example.review.domain.event.ReviewUpdatedPayload;
import com.example.review.infrastructure.event.dto.ReviewEventMessage;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ReviewEventMessage 매핑 및 직렬화 테스트")
class ReviewEventMessageMappingTest {

    private OutboxReviewEventPublisher publisher;
    private ObjectMapper objectMapper;

    private static final Instant FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock fixedClock = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        publisher = new OutboxReviewEventPublisher(null, objectMapper);
    }

    @Test
    @DisplayName("ReviewCreated 도메인 이벤트를 ReviewEventMessage로 올바르게 매핑한다")
    void toMessage_reviewCreatedEvent_mapsAllFields() {
        ReviewCreatedPayload payload = new ReviewCreatedPayload(
                "review-id-123", "product-id-456", "user-id-789", 5, "2024-01-01T00:00:00Z");
        ReviewEvent event = ReviewEvent.created(payload, fixedClock);

        ReviewEventMessage message = publisher.toMessage(event);

        assertThat(message.eventId()).isEqualTo(event.eventId());
        assertThat(message.eventType()).isEqualTo("ReviewCreated");
        assertThat(message.occurredAt()).isEqualTo(event.occurredAt());
        assertThat(message.source()).isEqualTo("review-service");
        assertThat(message.payload()).isSameAs(payload);
    }

    @Test
    @DisplayName("ReviewUpdated 도메인 이벤트를 ReviewEventMessage로 올바르게 매핑한다")
    void toMessage_reviewUpdatedEvent_mapsAllFields() {
        ReviewUpdatedPayload payload = new ReviewUpdatedPayload(
                "review-id-123", "product-id-456", "user-id-789", 4, "2024-01-02T00:00:00Z");
        ReviewEvent event = ReviewEvent.updated(payload, fixedClock);

        ReviewEventMessage message = publisher.toMessage(event);

        assertThat(message.eventType()).isEqualTo("ReviewUpdated");
        assertThat(message.payload()).isSameAs(payload);
    }

    @Test
    @DisplayName("ReviewDeleted 도메인 이벤트를 ReviewEventMessage로 올바르게 매핑한다")
    void toMessage_reviewDeletedEvent_mapsAllFields() {
        ReviewDeletedPayload payload = new ReviewDeletedPayload(
                "review-id-123", "product-id-456", "user-id-789", "2024-01-03T00:00:00Z");
        ReviewEvent event = ReviewEvent.deleted(payload, fixedClock);

        ReviewEventMessage message = publisher.toMessage(event);

        assertThat(message.eventType()).isEqualTo("ReviewDeleted");
        assertThat(message.payload()).isSameAs(payload);
    }

    @Test
    @DisplayName("ReviewEventMessage 직렬화 결과에 snake_case 엔벨로프 필드가 포함된다")
    void toMessage_serialized_containsSnakeCaseEnvelopeFields() throws Exception {
        ReviewCreatedPayload payload = new ReviewCreatedPayload(
                "review-id-123", "product-id-456", "user-id-789", 5, "2024-01-01T00:00:00Z");
        ReviewEvent event = ReviewEvent.created(payload, fixedClock);

        ReviewEventMessage message = publisher.toMessage(event);
        String json = objectMapper.writeValueAsString(message);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("event_id")).isTrue();
        assertThat(node.has("event_type")).isTrue();
        assertThat(node.has("occurred_at")).isTrue();
        assertThat(node.get("event_type").asText()).isEqualTo("ReviewCreated");
        assertThat(node.get("source").asText()).isEqualTo("review-service");
    }

    @Test
    @DisplayName("ReviewEventMessage 직렬화 결과에 camelCase 필드가 없다 (하위 호환성 확인)")
    void toMessage_serialized_doesNotContainCamelCaseEnvelopeFields() throws Exception {
        ReviewCreatedPayload payload = new ReviewCreatedPayload(
                "review-id-123", "product-id-456", "user-id-789", 5, "2024-01-01T00:00:00Z");
        ReviewEvent event = ReviewEvent.created(payload, fixedClock);

        ReviewEventMessage message = publisher.toMessage(event);
        String json = objectMapper.writeValueAsString(message);
        JsonNode node = objectMapper.readTree(json);

        assertThat(node.has("eventId")).isFalse();
        assertThat(node.has("eventType")).isFalse();
        assertThat(node.has("occurredAt")).isFalse();
    }
}
