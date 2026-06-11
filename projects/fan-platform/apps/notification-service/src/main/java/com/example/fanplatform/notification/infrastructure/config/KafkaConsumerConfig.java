package com.example.fanplatform.notification.infrastructure.config;

import com.example.fanplatform.notification.application.consumer.MalformedEventException;
import com.example.fanplatform.notification.application.consumer.UnsupportedSchemaVersionException;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.Map;

/**
 * Consumer retry + dead-letter wiring (consumer-retry-dlq.md;
 * platform/event-driven-policy.md Retry Policy: base 1s, ×2, max 30s, 3
 * attempts). On exhaustion (or immediately for a non-retryable exception) the
 * record is published to {@code <topic>.dlq} — the platform {@code .dlq} suffix,
 * NOT Spring Kafka's default {@code .DLT} — via a custom destination resolver.
 *
 * <p>This single {@link DefaultErrorHandler} bean is auto-wired by Spring Boot
 * into the auto-configured listener container factory. The DLQ
 * {@link KafkaTemplate} is the ONLY Kafka producer in this service (terminal
 * consumer — it publishes no domain events); values are always {@code String}
 * (the consumer reads raw {@code String}, so there is no deserialization-failure
 * byte[] path).
 */
@Slf4j
@Configuration
public class KafkaConsumerConfig {

    /** Counter incremented every time a record is routed to a DLQ topic. */
    public static final String DLQ_METRIC = "notification_dlq_total";

    @Bean
    public DefaultErrorHandler errorHandler(KafkaProperties kafkaProperties, MeterRegistry meterRegistry) {
        Map<String, Object> producerProps = kafkaProperties.buildProducerProperties(null);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        KafkaTemplate<String, String> dlqTemplate = new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(producerProps));

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dlqTemplate,
                (record, ex) -> {
                    String dlqTopic = record.topic() + ".dlq";
                    meterRegistry.counter(DLQ_METRIC, "topic", record.topic()).increment();
                    log.warn("Routing to DLQ: topic={}, dlq={}, key={}, offset={}, reason={}",
                            record.topic(), dlqTopic, record.key(), record.offset(),
                            ex.getMessage());
                    return new TopicPartition(dlqTopic, record.partition());
                });

        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxInterval(30000L);
        backOff.setMaxAttempts(3);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        // Non-retryable: a payload that will never parse / an unsupported schema
        // version — straight to DLQ, no pointless 3× retry.
        errorHandler.addNotRetryableExceptions(
                MalformedEventException.class,
                UnsupportedSchemaVersionException.class);
        return errorHandler;
    }
}
