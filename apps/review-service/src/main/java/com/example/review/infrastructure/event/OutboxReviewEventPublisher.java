package com.example.review.infrastructure.event;

import com.example.messaging.outbox.OutboxWriter;
import com.example.review.domain.event.ReviewEvent;
import com.example.review.domain.event.ReviewEventPublisher;
import com.example.review.infrastructure.event.dto.ReviewEventMessage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxReviewEventPublisher implements ReviewEventPublisher {

    private final OutboxWriter outboxWriter;
    private final ObjectMapper objectMapper;

    @Override
    public void publish(ReviewEvent event) {
        ReviewEventMessage message = toMessage(event);
        String serialized = serialize(message);
        String aggregateId = extractAggregateId(event);
        outboxWriter.save("Review", aggregateId, event.eventType(), serialized);
        log.debug("Saved review event to outbox: eventType={}, aggregateId={}", event.eventType(), aggregateId);
    }

    ReviewEventMessage toMessage(ReviewEvent event) {
        return new ReviewEventMessage(
                event.eventId(),
                event.eventType(),
                event.occurredAt(),
                event.source(),
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
