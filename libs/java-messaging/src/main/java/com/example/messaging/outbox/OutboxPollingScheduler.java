package com.example.messaging.outbox;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Base class for Outbox polling scheduler.
 *
 * Subclasses must implement {@link #resolveTopic(String)} to map event types
 * to Kafka topic names specific to each service.
 *
 * Subclasses may override {@link #onKafkaSendFailure(String, String, Exception)}
 * to add service-specific failure handling (e.g., metrics recording).
 */
@Slf4j
@RequiredArgsConstructor
public abstract class OutboxPollingScheduler {

    private final OutboxPublisher outboxPublisher;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelayString = "${outbox.polling.interval-ms:1000}")
    public void pollAndPublish() {
        outboxPublisher.publishPendingEvents(this::sendToKafka);
    }

    private boolean sendToKafka(String eventType, String aggregateId, String payload) {
        try {
            String topic = resolveTopic(eventType);
            kafkaTemplate.send(topic, aggregateId, payload).get();
            return true;
        } catch (Exception e) {
            log.error("Kafka send failed: eventType={}, aggregateId={}", eventType, aggregateId, e);
            onKafkaSendFailure(eventType, aggregateId, e);
            return false;
        }
    }

    /**
     * Maps an event type to the corresponding Kafka topic name.
     *
     * @param eventType the domain event type name (e.g. "OrderPlaced")
     * @return the Kafka topic name
     * @throws IllegalArgumentException if the event type is unknown
     */
    protected abstract String resolveTopic(String eventType);

    /**
     * Hook called when Kafka send fails. Subclasses can override to record metrics
     * or perform other failure handling.
     *
     * @param eventType   the event type that failed to send
     * @param aggregateId the aggregate ID
     * @param e           the exception that caused the failure
     */
    protected void onKafkaSendFailure(String eventType, String aggregateId, Exception e) {
        // default: no additional handling
    }
}
