package com.example.messaging.outbox;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.lang.Nullable;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Outbox polling scheduler.
 *
 * <p>Reads {@code outbox.topic-mapping} from configuration and publishes pending
 * outbox rows to Kafka. Optional {@link OutboxFailureHandler} can be provided
 * (e.g. to increment Micrometer counters) without adding Micrometer as a
 * compile-time dependency to this library.
 *
 * <h2>Lifecycle (TASK-BE-077)</h2>
 *
 * <p>Uses a dedicated {@link ThreadPoolTaskScheduler} ({@code outboxTaskScheduler})
 * whose lifetime is bound to the owning {@code ApplicationContext}. This prevents
 * the orphaned-thread / closing-pool issue described in PR #44 / TASK-BE-076.
 */
@Slf4j
public class OutboxPollingScheduler {

    private final OutboxPublisher outboxPublisher;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ThreadPoolTaskScheduler taskScheduler;
    private final Map<String, String> topicMapping;
    @Nullable
    private final OutboxFailureHandler failureHandler;

    @Value("${outbox.polling.interval-ms:1000}")
    private long intervalMs;

    private final AtomicBoolean running = new AtomicBoolean(true);
    private ScheduledFuture<?> scheduledFuture;

    public OutboxPollingScheduler(OutboxPublisher outboxPublisher,
                                  KafkaTemplate<String, String> kafkaTemplate,
                                  ThreadPoolTaskScheduler outboxTaskScheduler,
                                  OutboxProperties outboxProperties,
                                  @Nullable OutboxFailureHandler failureHandler) {
        this.outboxPublisher = outboxPublisher;
        this.kafkaTemplate = kafkaTemplate;
        this.taskScheduler = outboxTaskScheduler;
        this.topicMapping = outboxProperties.getTopicMapping();
        this.failureHandler = failureHandler;
    }

    @PostConstruct
    public void start() {
        scheduledFuture = taskScheduler.scheduleWithFixedDelay(
                this::pollAndPublish, Duration.ofMillis(intervalMs));
        log.info("OutboxPollingScheduler started: intervalMs={}", intervalMs);
    }

    public void pollAndPublish() {
        if (!running.get()) {
            return;
        }
        try {
            outboxPublisher.publishPendingEvents(this::sendToKafka);
        } catch (Exception e) {
            if (!running.get()) {
                log.debug("OutboxPollingScheduler tick failed during shutdown; suppressing.", e);
            } else {
                log.error("Unexpected error during outbox poll tick.", e);
            }
        }
    }

    @PreDestroy
    public void stop() {
        if (running.compareAndSet(true, false)) {
            log.info("OutboxPollingScheduler stop requested; cancelling scheduled task.");
            if (scheduledFuture != null) {
                scheduledFuture.cancel(false);
            }
        }
    }

    private boolean sendToKafka(String eventType, String aggregateId, String payload) {
        try {
            String topic = resolveTopic(eventType);
            kafkaTemplate.send(topic, aggregateId, payload).get();
            return true;
        } catch (Exception e) {
            log.error("Kafka send failed: eventType={}, aggregateId={}", eventType, aggregateId, e);
            if (failureHandler != null) {
                failureHandler.onFailure(eventType, aggregateId, e);
            }
            return false;
        }
    }

    private String resolveTopic(String eventType) {
        String topic = topicMapping.get(eventType);
        if (topic == null) {
            throw new IllegalStateException("No topic mapping for event type: " + eventType);
        }
        return topic;
    }
}
