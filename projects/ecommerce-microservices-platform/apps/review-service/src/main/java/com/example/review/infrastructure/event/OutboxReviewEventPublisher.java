package com.example.review.infrastructure.event;

import com.example.review.domain.event.ReviewEvent;
import com.example.review.domain.event.ReviewEventPublisher;
import com.example.review.infrastructure.event.dto.ReviewEventMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * review-service outbox write path (TASK-BE-445, outbox v2).
 *
 * <p>Persists one {@link ReviewOutboxEntity} ({@code review_outbox} table) per
 * domain event inside the caller's transaction, so the business mutation and the
 * outbox row commit atomically. The {@link ReviewOutboxPublisher} relay drains
 * the table to Kafka.
 *
 * <p>Replaces the v1 lib {@code OutboxWriter} ({@code OutboxJpaEntity},
 * server-assigned {@code BIGSERIAL}, {@code status} string). Wire is preserved
 * exactly:
 * <ul>
 *   <li>{@code payload} = {@code objectMapper.writeValueAsString(toMessage(event))}
 *       — the {@link ReviewEventMessage} envelope JSON, byte-identical to v1.</li>
 *   <li>{@code aggregate_type}/{@code aggregate_id}/{@code eventType} =
 *       {@code "Review"}/{@code reviewId}/{@code event.eventType()}, exactly as
 *       the v1 {@code outboxWriter.save(...)} call. {@code aggregate_id} becomes
 *       the Kafka record key (per-review ordering) since {@code partition_key} is
 *       null.</li>
 *   <li>{@code event_id} reuses the event's own envelope {@code eventId} (a UUID)
 *       so the Kafka header {@code eventId} matches the payload {@code event_id} —
 *       no second identifier, no payload mutation.</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxReviewEventPublisher implements ReviewEventPublisher {

    private static final String AGGREGATE_TYPE = "Review";

    private final ReviewOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(ReviewEvent event) {
        String aggregateId = extractAggregateId(event);
        ReviewOutboxEntity row = new ReviewOutboxEntity(
                event.eventId(),
                event.eventType(),
                AGGREGATE_TYPE,
                aggregateId,
                null, // partition_key: publisher falls back to aggregateId (reviewId)
                serialize(toMessage(event)),
                event.occurredAt());
        outboxRepository.save(row);
        log.debug("Saved review event to outbox: eventType={}, aggregateId={}", event.eventType(), aggregateId);
    }

    ReviewEventMessage toMessage(ReviewEvent event) {
        return new ReviewEventMessage(
                event.eventId(),
                event.eventType(),
                event.occurredAt(),
                event.source(),
                event.tenantId(),
                event.payload()
        );
    }

    private String extractAggregateId(ReviewEvent event) {
        return event.payload().reviewId();
    }

    private String serialize(ReviewEventMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize ReviewEventMessage", e);
        }
    }
}
