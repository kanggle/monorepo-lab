package com.example.security.infrastructure.event;

import com.example.messaging.outbox.OutboxPollingScheduler;
import com.example.messaging.outbox.OutboxPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SecurityOutboxPollingScheduler extends OutboxPollingScheduler {

    static final String TOPIC_SUSPICIOUS_DETECTED = "security.suspicious.detected";
    static final String TOPIC_AUTO_LOCK_TRIGGERED = "security.auto.lock.triggered";
    static final String TOPIC_AUTO_LOCK_PENDING = "security.auto.lock.pending";
    // TASK-BE-258: GDPR compliance audit trail topic
    static final String TOPIC_PII_MASKED = "security.pii.masked";

    public SecurityOutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                          KafkaTemplate<String, String> kafkaTemplate) {
        super(outboxPublisher, kafkaTemplate);
    }

    @Override
    protected String resolveTopic(String eventType) {
        return switch (eventType) {
            case "security.suspicious.detected" -> TOPIC_SUSPICIOUS_DETECTED;
            case "security.auto.lock.triggered" -> TOPIC_AUTO_LOCK_TRIGGERED;
            case "security.auto.lock.pending" -> TOPIC_AUTO_LOCK_PENDING;
            case "security.pii.masked" -> TOPIC_PII_MASKED;
            default -> throw new IllegalArgumentException("Unknown security event type: " + eventType);
        };
    }
}
