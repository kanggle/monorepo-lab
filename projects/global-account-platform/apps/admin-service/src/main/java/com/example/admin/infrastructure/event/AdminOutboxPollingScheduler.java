package com.example.admin.infrastructure.event;

import com.example.messaging.outbox.OutboxPollingScheduler;
import com.example.messaging.outbox.OutboxPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AdminOutboxPollingScheduler extends OutboxPollingScheduler {

    static final String TOPIC_ACTION_PERFORMED = "admin.action.performed";

    public AdminOutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                       KafkaTemplate<String, String> kafkaTemplate) {
        super(outboxPublisher, kafkaTemplate);
    }

    @Override
    protected String resolveTopic(String eventType) {
        return switch (eventType) {
            case "admin.action.performed" -> TOPIC_ACTION_PERFORMED;
            default -> throw new IllegalArgumentException("Unknown admin event type: " + eventType);
        };
    }
}
