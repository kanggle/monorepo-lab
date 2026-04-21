package com.example.review.infrastructure.event;

import com.example.messaging.outbox.OutboxPublisher;
import com.example.messaging.outbox.OutboxPollingScheduler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class ReviewOutboxPollingScheduler extends OutboxPollingScheduler {

    static final String TOPIC_REVIEW_CREATED = "review.review.created";
    static final String TOPIC_REVIEW_UPDATED = "review.review.updated";
    static final String TOPIC_REVIEW_DELETED = "review.review.deleted";

    public ReviewOutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                        KafkaTemplate<String, String> kafkaTemplate) {
        super(outboxPublisher, kafkaTemplate);
    }

    @Override
    protected String resolveTopic(String eventType) {
        return switch (eventType) {
            case "ReviewCreated" -> TOPIC_REVIEW_CREATED;
            case "ReviewUpdated" -> TOPIC_REVIEW_UPDATED;
            case "ReviewDeleted" -> TOPIC_REVIEW_DELETED;
            default -> throw new IllegalArgumentException("Unknown review event type: " + eventType);
        };
    }
}
