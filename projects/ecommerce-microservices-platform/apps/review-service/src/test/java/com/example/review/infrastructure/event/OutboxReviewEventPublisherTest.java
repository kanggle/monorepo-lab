package com.example.review.infrastructure.event;

import com.example.messaging.outbox.OutboxWriter;
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

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxReviewEventPublisher 단위 테스트")
class OutboxReviewEventPublisherTest {

    @Mock
    private OutboxWriter outboxWriter;

    private OutboxReviewEventPublisher publisher;

    private static final Instant FIXED_TIME = Instant.parse("2026-01-01T00:00:00Z");
    private final Clock fixedClock = Clock.fixed(FIXED_TIME, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        publisher = new OutboxReviewEventPublisher(outboxWriter, objectMapper);
    }

    @Test
    @DisplayName("ReviewCreated 이벤트를 Outbox 테이블에 저장한다")
    void publish_reviewCreatedEvent_savesToOutbox() {
        ReviewCreatedPayload payload = new ReviewCreatedPayload(
                "review-id-123", "product-id-456", "user-id-789", 5, "2024-01-01T00:00:00Z");
        ReviewEvent event = ReviewEvent.created(payload, fixedClock);

        publisher.publish(event);

        ArgumentCaptor<String> aggregateTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> aggregateIdCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);

        verify(outboxWriter).save(
                aggregateTypeCaptor.capture(),
                aggregateIdCaptor.capture(),
                eventTypeCaptor.capture(),
                payloadCaptor.capture()
        );

        assertThat(aggregateTypeCaptor.getValue()).isEqualTo("Review");
        assertThat(aggregateIdCaptor.getValue()).isEqualTo("review-id-123");
        assertThat(eventTypeCaptor.getValue()).isEqualTo("ReviewCreated");
        assertThat(payloadCaptor.getValue()).contains("ReviewCreated");
        assertThat(payloadCaptor.getValue()).contains("review-id-123");
    }

    @Test
    @DisplayName("ReviewUpdated 이벤트를 Outbox 테이블에 저장한다")
    void publish_reviewUpdatedEvent_savesToOutbox() {
        ReviewUpdatedPayload payload = new ReviewUpdatedPayload(
                "review-id-123", "product-id-456", "user-id-789", 4, "2024-01-02T00:00:00Z");
        ReviewEvent event = ReviewEvent.updated(payload, fixedClock);

        publisher.publish(event);

        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> aggregateIdCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(
                org.mockito.ArgumentMatchers.anyString(),
                aggregateIdCaptor.capture(),
                eventTypeCaptor.capture(),
                org.mockito.ArgumentMatchers.anyString()
        );

        assertThat(eventTypeCaptor.getValue()).isEqualTo("ReviewUpdated");
        assertThat(aggregateIdCaptor.getValue()).isEqualTo("review-id-123");
    }

    @Test
    @DisplayName("ReviewDeleted 이벤트를 Outbox 테이블에 저장한다")
    void publish_reviewDeletedEvent_savesToOutbox() {
        ReviewDeletedPayload payload = new ReviewDeletedPayload(
                "review-id-123", "product-id-456", "user-id-789", "2024-01-03T00:00:00Z");
        ReviewEvent event = ReviewEvent.deleted(payload, fixedClock);

        publisher.publish(event);

        ArgumentCaptor<String> eventTypeCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                eventTypeCaptor.capture(),
                org.mockito.ArgumentMatchers.anyString()
        );

        assertThat(eventTypeCaptor.getValue()).isEqualTo("ReviewDeleted");
    }

    @Test
    @DisplayName("직렬화된 페이로드에 이벤트 엔벨로프 필드가 포함된다")
    void publish_serializedPayload_containsEnvelopeFields() {
        ReviewCreatedPayload payload = new ReviewCreatedPayload(
                "review-id-123", "product-id-456", "user-id-789", 5, "2024-01-01T00:00:00Z");
        ReviewEvent event = ReviewEvent.created(payload, fixedClock);

        publisher.publish(event);

        ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
        verify(outboxWriter).save(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                payloadCaptor.capture()
        );

        String serializedPayload = payloadCaptor.getValue();
        assertThat(serializedPayload).contains("event_id");
        assertThat(serializedPayload).contains("event_type");
        assertThat(serializedPayload).contains("occurred_at");
        assertThat(serializedPayload).contains("source");
        assertThat(serializedPayload).contains("review-service");
    }
}
