package com.example.order.infrastructure.event;

import com.example.messaging.outbox.OutboxPublisher;
import com.example.order.application.port.OrderMetricsPort;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@org.springframework.context.annotation.Profile("!standalone")
public class OutboxPollingScheduler extends com.example.messaging.outbox.OutboxPollingScheduler {

    static final String TOPIC_ORDER_PLACED = "order.order.placed";
    static final String TOPIC_ORDER_CANCELLED = "order.order.cancelled";

    private final OrderMetricsPort orderMetrics;

    public OutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  OrderMetricsPort orderMetrics) {
        super(outboxPublisher, kafkaTemplate);
        this.orderMetrics = orderMetrics;
    }

    @Override
    protected String resolveTopic(String eventType) {
        return switch (eventType) {
            case "OrderPlaced" -> TOPIC_ORDER_PLACED;
            case "OrderCancelled" -> TOPIC_ORDER_CANCELLED;
            default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
        };
    }

    @Override
    protected void onKafkaSendFailure(String eventType, String aggregateId, Exception e) {
        orderMetrics.recordEventPublishFailure(eventType);
    }
}
