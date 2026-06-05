package com.example.account.infrastructure.event;

import com.example.messaging.outbox.OutboxPollingScheduler;
import com.example.messaging.outbox.OutboxPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AccountOutboxPollingScheduler extends OutboxPollingScheduler {

    static final String TOPIC_CREATED = "account.created";
    static final String TOPIC_STATUS_CHANGED = "account.status.changed";
    static final String TOPIC_LOCKED = "account.locked";
    static final String TOPIC_UNLOCKED = "account.unlocked";
    static final String TOPIC_ROLES_CHANGED = "account.roles.changed";
    static final String TOPIC_DELETED = "account.deleted";

    public AccountOutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                         KafkaTemplate<String, String> kafkaTemplate) {
        super(outboxPublisher, kafkaTemplate);
    }

    @Override
    protected String resolveTopic(String eventType) {
        return switch (eventType) {
            case "account.created" -> TOPIC_CREATED;
            case "account.status.changed" -> TOPIC_STATUS_CHANGED;
            case "account.locked" -> TOPIC_LOCKED;
            case "account.unlocked" -> TOPIC_UNLOCKED;
            case "account.roles.changed" -> TOPIC_ROLES_CHANGED;
            case "account.deleted" -> TOPIC_DELETED;
            default -> throw new IllegalArgumentException("Unknown account event type: " + eventType);
        };
    }
}
