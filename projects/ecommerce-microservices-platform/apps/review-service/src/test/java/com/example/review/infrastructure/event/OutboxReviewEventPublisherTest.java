package com.example.review.infrastructure.event;

import com.example.review.domain.event.ReviewCreatedPayload;
import com.example.review.domain.event.ReviewDeletedPayload;
import com.example.review.domain.event.ReviewEvent;
import com.example.review.domain.event.ReviewUpdatedPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

/**
 * Unit test for the {@link OutboxReviewEventPublisher} write path (TASK-BE-445,
 * outbox v2). Asserts each domain event persists a {@code review_outbox} row
 * whose wire-relevant fields are preserved exactly: the row {@code event_id}
 * reuses the event envelope {@code eventId}, the payload is the byte-identical
 * {@code writeValueAsString(toMessage(event))} ReviewEventMessage envelope, the
 * key source ({@code aggregate_id}) is the {@code reviewId}, and
 * {@code occurred_at} is the domain timestamp.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxReviewEventPublisher 단위 테스트 (outbox v2)")
class OutboxReviewEventPublisherTest {

    @Mock
    private ReviewOutboxRepository outboxRepository;

    private OutboxReviewEventPublisher publisher;
    private ObjectMapper objectMapper;

    private static final Instant FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock fixedClock = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        publisher = new OutboxReviewEventPublisher(outboxRepository, objectMapper);
    }

    @Test
    @DisplayName("ReviewCreated 이벤트를 review_outbox v2 행으로 저장한다")
    void publish_reviewCreated_savesV2Row() throws Exception {
        ReviewCreatedPayload payload = new ReviewCreatedPayload(
                "review-id-123", "product-id-456", "user-id-789", 5, "2024-01-01T00:00:00Z");
        ReviewEvent event = ReviewEvent.created(payload, "ecommerce", fixedClock);

        publisher.publish(event);

        ReviewOutboxEntity row = capturedRow();
        assertThat(row.getEventId()).isEqualTo(event.eventId());
        assertThat(row.getEventType()).isEqualTo("ReviewCreated");
        assertThat(row.getAggregateType()).isEqualTo("Review");
        assertThat(row.getAggregateId()).isEqualTo("review-id-123");
        assertThat(row.getPartitionKey()).isNull();
        assertThat(row.getOccurredAt()).isEqualTo(event.occurredAt());
        assertThat(row.getPayload()).isEqualTo(objectMapper.writeValueAsString(publisher.toMessage(event)));
        assertThat(row.getPublishedAt()).isNull();
    }

    @Test
    @DisplayName("ReviewUpdated 이벤트를 review_outbox v2 행으로 저장한다")
    void publish_reviewUpdated_savesV2Row() {
        ReviewUpdatedPayload payload = new ReviewUpdatedPayload(
                "review-id-123", "product-id-456", "user-id-789", 4, "2024-01-02T00:00:00Z");
        ReviewEvent event = ReviewEvent.updated(payload, "ecommerce", fixedClock);

        publisher.publish(event);

        ReviewOutboxEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo("ReviewUpdated");
        assertThat(row.getAggregateId()).isEqualTo("review-id-123");
        assertThat(row.getEventId()).isEqualTo(event.eventId());
    }

    @Test
    @DisplayName("ReviewDeleted 이벤트를 review_outbox v2 행으로 저장한다")
    void publish_reviewDeleted_savesV2Row() {
        ReviewDeletedPayload payload = new ReviewDeletedPayload(
                "review-id-123", "product-id-456", "user-id-789", "2024-01-03T00:00:00Z");
        ReviewEvent event = ReviewEvent.deleted(payload, "ecommerce", fixedClock);

        publisher.publish(event);

        ReviewOutboxEntity row = capturedRow();
        assertThat(row.getEventType()).isEqualTo("ReviewDeleted");
        assertThat(row.getAggregateId()).isEqualTo("review-id-123");
    }

    @Test
    @DisplayName("저장된 payload 에 이벤트 엔벨로프 필드가 포함된다 (wire 보존)")
    void publish_serializedPayload_containsEnvelopeFields() throws Exception {
        ReviewCreatedPayload payload = new ReviewCreatedPayload(
                "review-id-123", "product-id-456", "user-id-789", 5, "2024-01-01T00:00:00Z");
        ReviewEvent event = ReviewEvent.created(payload, "ecommerce", fixedClock);

        publisher.publish(event);

        String serialized = capturedRow().getPayload();
        assertThat(serialized).contains("event_id");
        assertThat(serialized).contains("event_type");
        assertThat(serialized).contains("occurred_at");
        assertThat(serialized).contains("source");
        assertThat(serialized).contains("review-service");
        assertThat(serialized).contains("tenant_id");
        assertThat(serialized).contains("ecommerce");
    }

    private ReviewOutboxEntity capturedRow() {
        ArgumentCaptor<ReviewOutboxEntity> captor = ArgumentCaptor.forClass(ReviewOutboxEntity.class);
        verify(outboxRepository).save(captor.capture());
        return captor.getValue();
    }
}
