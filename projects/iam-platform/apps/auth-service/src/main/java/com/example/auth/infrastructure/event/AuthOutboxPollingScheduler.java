package com.example.auth.infrastructure.event;

import com.example.messaging.outbox.OutboxPollingScheduler;
import com.example.messaging.outbox.OutboxPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AuthOutboxPollingScheduler extends OutboxPollingScheduler {

    static final String TOPIC_LOGIN_ATTEMPTED = "auth.login.attempted";
    static final String TOPIC_LOGIN_FAILED = "auth.login.failed";
    static final String TOPIC_LOGIN_SUCCEEDED = "auth.login.succeeded";
    static final String TOPIC_TOKEN_REFRESHED = "auth.token.refreshed";
    static final String TOPIC_TOKEN_REUSE_DETECTED = "auth.token.reuse.detected";
    static final String TOPIC_TOKEN_TENANT_MISMATCH = "auth.token.tenant.mismatch";
    static final String TOPIC_SESSION_CREATED = "auth.session.created";
    static final String TOPIC_SESSION_REVOKED = "auth.session.revoked";

    public AuthOutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                      KafkaTemplate<String, String> kafkaTemplate) {
        super(outboxPublisher, kafkaTemplate);
    }

    @Override
    protected String resolveTopic(String eventType) {
        return switch (eventType) {
            case "auth.login.attempted" -> TOPIC_LOGIN_ATTEMPTED;
            case "auth.login.failed" -> TOPIC_LOGIN_FAILED;
            case "auth.login.succeeded" -> TOPIC_LOGIN_SUCCEEDED;
            case "auth.token.refreshed" -> TOPIC_TOKEN_REFRESHED;
            case "auth.token.reuse.detected" -> TOPIC_TOKEN_REUSE_DETECTED;
            case "auth.token.tenant.mismatch" -> TOPIC_TOKEN_TENANT_MISMATCH;
            case "auth.session.created" -> TOPIC_SESSION_CREATED;
            case "auth.session.revoked" -> TOPIC_SESSION_REVOKED;
            default -> throw new IllegalArgumentException("Unknown auth event type: " + eventType);
        };
    }
}
