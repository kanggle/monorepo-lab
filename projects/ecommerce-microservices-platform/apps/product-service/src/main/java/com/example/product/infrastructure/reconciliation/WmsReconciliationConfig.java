package com.example.product.infrastructure.reconciliation;

import com.fasterxml.jackson.core.JsonProcessingException;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.time.Clock;

/**
 * Wiring for the wms reconciliation leg (ADR-MONO-022 §D4 v2(b)) — product-service's
 * first inbound consumer. Provides a {@link Clock} (used by the dedupe + reconciliation
 * service) and a retry→DLQ error handler for the wms consumers.
 */
@Slf4j
@Configuration
public class WmsReconciliationConfig {

    @Bean
    public Clock reconciliationClock() {
        return Clock.systemUTC();
    }

    @Bean
    public CommonErrorHandler wmsReconciliationErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {
        var recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception ex) -> {
                    log.error("Sending wms reconciliation record to DLQ. topic={}, offset={}, error={}",
                            record.topic(), record.offset(), ex.getMessage());
                    return new TopicPartition(record.topic() + ".dlq", record.partition());
                });

        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxInterval(30000L);
        backOff.setMaxAttempts(3);

        var errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(
                JsonProcessingException.class,
                IllegalArgumentException.class);
        return errorHandler;
    }
}
