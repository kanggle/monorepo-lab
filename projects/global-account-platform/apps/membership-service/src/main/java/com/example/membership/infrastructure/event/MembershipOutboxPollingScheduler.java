package com.example.membership.infrastructure.event;

import com.example.messaging.outbox.OutboxPollingScheduler;
import com.example.messaging.outbox.OutboxPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class MembershipOutboxPollingScheduler extends OutboxPollingScheduler {

    static final String TOPIC_SUBSCRIPTION_ACTIVATED = "membership.subscription.activated";
    static final String TOPIC_SUBSCRIPTION_EXPIRED = "membership.subscription.expired";
    static final String TOPIC_SUBSCRIPTION_CANCELLED = "membership.subscription.cancelled";

    public MembershipOutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                            KafkaTemplate<String, String> kafkaTemplate) {
        super(outboxPublisher, kafkaTemplate);
    }

    @Override
    protected String resolveTopic(String eventType) {
        return switch (eventType) {
            case "membership.subscription.activated" -> TOPIC_SUBSCRIPTION_ACTIVATED;
            case "membership.subscription.expired" -> TOPIC_SUBSCRIPTION_EXPIRED;
            case "membership.subscription.cancelled" -> TOPIC_SUBSCRIPTION_CANCELLED;
            default -> throw new IllegalArgumentException("Unknown membership event type: " + eventType);
        };
    }
}
