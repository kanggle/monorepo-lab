package com.example.fanplatform.notification.infrastructure.messaging;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Per-topic consumer counters (architecture.md § Observability). Kafka client
 * {@code kafka.consumer.*} metrics (incl. {@code consumer_lag}) are bound
 * automatically by Spring Boot's Micrometer Kafka integration; this records the
 * handler-level processed / failed counts. The DLQ counter lives in
 * {@code KafkaConsumerConfig}'s recoverer.
 */
@Component
public class ConsumerMetrics {

    private static final String PROCESSED = "notification_messages_processed_total";
    private static final String FAILED = "notification_messages_failed_total";

    private final MeterRegistry registry;

    public ConsumerMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void processed(String topic) {
        registry.counter(PROCESSED, "topic", topic).increment();
    }

    public void failed(String topic) {
        registry.counter(FAILED, "topic", topic).increment();
    }
}
