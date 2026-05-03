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
    static final String TOPIC_TENANT_CREATED    = "tenant.created";
    static final String TOPIC_TENANT_SUSPENDED  = "tenant.suspended";
    static final String TOPIC_TENANT_REACTIVATED = "tenant.reactivated";
    static final String TOPIC_TENANT_UPDATED    = "tenant.updated";

    public AdminOutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                       KafkaTemplate<String, String> kafkaTemplate) {
        super(outboxPublisher, kafkaTemplate);
    }

    @Override
    protected String resolveTopic(String eventType) {
        return switch (eventType) {
            case "admin.action.performed" -> TOPIC_ACTION_PERFORMED;
            case "tenant.created"         -> TOPIC_TENANT_CREATED;
            case "tenant.suspended"       -> TOPIC_TENANT_SUSPENDED;
            case "tenant.reactivated"     -> TOPIC_TENANT_REACTIVATED;
            case "tenant.updated"         -> TOPIC_TENANT_UPDATED;
            default -> throw new IllegalArgumentException("Unknown admin event type: " + eventType);
        };
    }
}
