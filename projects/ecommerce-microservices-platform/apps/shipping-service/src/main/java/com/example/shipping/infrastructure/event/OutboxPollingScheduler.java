package com.example.shipping.infrastructure.event;

import com.example.messaging.outbox.OutboxPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OutboxPollingScheduler extends com.example.messaging.outbox.OutboxPollingScheduler {

    static final String TOPIC_SHIPPING_STATUS_CHANGED = "shipping.shipping.status-changed";

    public OutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                  KafkaTemplate<String, String> kafkaTemplate) {
        super(outboxPublisher, kafkaTemplate);
    }

    @Override
    protected String resolveTopic(String eventType) {
        return switch (eventType) {
            case "ShippingStatusChanged" -> TOPIC_SHIPPING_STATUS_CHANGED;
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}
