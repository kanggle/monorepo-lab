package com.wms.master.adapter.out.messaging;

import com.example.messaging.outbox.OutboxPollingScheduler;
import com.example.messaging.outbox.OutboxPublisher;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Master-service outbox polling scheduler.
 * <p>
 * Maps {@code master.<aggregate>.*} event types to their v1 topics per
 * {@code specs/contracts/events/master-events.md} § Topic Layout.
 */
public class MasterOutboxPollingScheduler extends OutboxPollingScheduler {

    private static final String TOPIC_PREFIX = "wms.master.";
    private static final String TOPIC_SUFFIX = ".v1";

    private final OutboxMetrics metrics;

    public MasterOutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                        KafkaTemplate<String, String> kafkaTemplate,
                                        OutboxMetrics metrics) {
        super(outboxPublisher, kafkaTemplate);
        this.metrics = metrics;
    }

    /**
     * Resolves {@code master.warehouse.created} → {@code wms.master.warehouse.v1}.
     * <p>
     * Event types that do not follow {@code master.<aggregate>.<action>} are
     * rejected — the outbox is master-service-only in v1.
     */
    @Override
    protected String resolveTopic(String eventType) {
        if (eventType == null || !eventType.startsWith("master.")) {
            throw new IllegalArgumentException("Unsupported event type: " + eventType);
        }
        String[] parts = eventType.split("\\.");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Unsupported event type: " + eventType);
        }
        return TOPIC_PREFIX + parts[1] + TOPIC_SUFFIX;
    }

    @Override
    protected void onKafkaSendFailure(String eventType, String aggregateId, Exception e) {
        metrics.recordPublishFailure();
    }
}
