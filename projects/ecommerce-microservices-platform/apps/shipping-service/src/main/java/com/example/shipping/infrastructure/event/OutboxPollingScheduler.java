package com.example.shipping.infrastructure.event;

import com.example.messaging.outbox.OutboxPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OutboxPollingScheduler extends com.example.messaging.outbox.OutboxPollingScheduler {

    static final String TOPIC_SHIPPING_STATUS_CHANGED = "shipping.shipping.status-changed";
    static final String TOPIC_FULFILLMENT_REQUESTED = "ecommerce.fulfillment.requested.v1";
    static final String TOPIC_MANUAL_SHIP_CONFIRM_REQUESTED = "ecommerce.shipping.manual-confirm-requested.v1";

    public OutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                  KafkaTemplate<String, String> kafkaTemplate) {
        super(outboxPublisher, kafkaTemplate);
    }

    @Override
    protected String resolveTopic(String eventType) {
        return switch (eventType) {
            case "ShippingStatusChanged" -> TOPIC_SHIPPING_STATUS_CHANGED;
            case "FulfillmentRequested" -> TOPIC_FULFILLMENT_REQUESTED;
            case "ManualShipConfirmRequested" -> TOPIC_MANUAL_SHIP_CONFIRM_REQUESTED;
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }
}
