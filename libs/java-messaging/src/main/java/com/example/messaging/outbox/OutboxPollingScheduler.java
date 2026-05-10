package com.example.messaging.outbox;

import com.example.messaging.event.EventSerializationException;
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

    private OutboxPublisher.SendOutcome sendToKafka(String eventType, String aggregateId, String payload) {
        String topic;
        try {
            topic = resolveTopic(eventType);
        } catch (IllegalArgumentException e) {
            log.error("Outbox row has unresolved event type — marking row FAILED: eventType={}, aggregateId={}",
                    eventType, aggregateId, e);
            onPermanentFailure(eventType, aggregateId, e);
            return OutboxPublisher.SendOutcome.FAILURE_PERMANENT;
        }
        try {
            kafkaTemplate.send(topic, aggregateId, payload).get();
            onKafkaSendSuccess(eventType, aggregateId);
            return OutboxPublisher.SendOutcome.SUCCESS;
        } catch (EventSerializationException e) {
            log.error("Outbox row has unserializable payload — marking row FAILED: eventType={}, aggregateId={}",
                    eventType, aggregateId, e);
            onPermanentFailure(eventType, aggregateId, e);
            return OutboxPublisher.SendOutcome.FAILURE_PERMANENT;
        } catch (Exception e) {
            log.error("Kafka send failed (transient, will retry): eventType={}, aggregateId={}",
                    eventType, aggregateId, e);
            onKafkaSendFailure(eventType, aggregateId, e);
            return OutboxPublisher.SendOutcome.FAILURE_TRANSIENT;
        }
    }

    /**
     * Maps an event type to the corresponding Kafka topic name.
     *
     * @param eventType the domain event type name (e.g. "OrderPlaced")
     * @return the Kafka topic name
     * @throws IllegalArgumentException if the event type is unknown — the
     *         outbox row will be terminally marked FAILED rather than blocking
     *         the batch.
     */
    protected abstract String resolveTopic(String eventType);

    /**
     * Hook called when Kafka send fails with a transient error (broker
     * unreachable, timeout, etc.). The row remains PENDING and the batch
     * drain breaks; the next poll retries the same row. Subclasses can
     * override to record metrics.
     */
    protected void onKafkaSendFailure(String eventType, String aggregateId, Exception e) {
        // default: no additional handling
    }

    /**
     * Hook called when Kafka send succeeds (broker acknowledgment received).
     * Subclasses can override to record metrics. Default: no-op.
     */
    protected void onKafkaSendSuccess(String eventType, String aggregateId) {
        // default: no additional handling
    }

    /**
     * Hook called when the row is being terminally marked FAILED due to a
     * permanent error (unknown {@code eventType}, unserializable payload).
     * The row will NOT be retried; operators must inspect the
     * {@code outbox.failure_reason} column. Subclasses can override to
     * publish to a dead-letter topic or fire an alert. Default: log only.
     */
    protected void onPermanentFailure(String eventType, String aggregateId, Exception e) {
        // default: log already emitted by sendToKafka
    }
}
