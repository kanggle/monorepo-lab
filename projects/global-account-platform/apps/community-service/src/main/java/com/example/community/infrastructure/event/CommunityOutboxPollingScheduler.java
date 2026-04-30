package com.example.community.infrastructure.event;

import com.example.messaging.outbox.OutboxPollingScheduler;
import com.example.messaging.outbox.OutboxPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class CommunityOutboxPollingScheduler extends OutboxPollingScheduler {

    static final String TOPIC_POST_PUBLISHED = "community.post.published";
    static final String TOPIC_COMMENT_CREATED = "community.comment.created";
    static final String TOPIC_REACTION_ADDED = "community.reaction.added";

    public CommunityOutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                           KafkaTemplate<String, String> kafkaTemplate) {
        super(outboxPublisher, kafkaTemplate);
    }

    @Override
    protected String resolveTopic(String eventType) {
        return switch (eventType) {
            case "community.post.published" -> TOPIC_POST_PUBLISHED;
            case "community.comment.created" -> TOPIC_COMMENT_CREATED;
            case "community.reaction.added" -> TOPIC_REACTION_ADDED;
            default -> throw new IllegalArgumentException("Unknown community event type: " + eventType);
        };
    }
}
