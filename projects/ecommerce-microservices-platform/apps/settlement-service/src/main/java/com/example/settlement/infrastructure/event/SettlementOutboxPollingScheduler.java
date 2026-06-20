package com.example.settlement.infrastructure.event;

import com.example.messaging.outbox.OutboxPublisher;
import com.example.settlement.application.event.SettlementPeriodClosedEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Relays settlement outbox rows to Kafka (architecture.md § Outbox). Maps the only
 * published event type, {@code settlement.period.closed.v1}, to topic
 * {@code settlement.period.closed} (settlement-events.md). Mirrors order-service's
 * {@code OutboxPollingScheduler}.
 *
 * <p>{@code @Profile("!standalone")} so the standalone (H2) profile has no relay.
 */
@Component
@org.springframework.context.annotation.Profile("!standalone")
public class SettlementOutboxPollingScheduler
        extends com.example.messaging.outbox.OutboxPollingScheduler {

    static final String TOPIC_PERIOD_CLOSED = "settlement.period.closed";

    public SettlementOutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                            KafkaTemplate<String, String> kafkaTemplate) {
        super(outboxPublisher, kafkaTemplate);
    }

    @Override
    protected String resolveTopic(String eventType) {
        if (SettlementPeriodClosedEvent.EVENT_TYPE.equals(eventType)) {
            return TOPIC_PERIOD_CLOSED;
        }
        throw new IllegalArgumentException("Unknown event type: " + eventType);
    }
}
