package com.wms.notification.config;

import java.time.Duration;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * Wires the shared Kafka error handler used by every {@code @KafkaListener}
 * in this service.
 *
 * <ul>
 *   <li>Up to 3 retries with exponential backoff (1s → 2s → 4s, cap 8s).</li>
 *   <li>After retries are exhausted, route the record to {@code <topic>.DLT}.</li>
 *   <li>Non-retryable failures ({@link IllegalArgumentException} from envelope
 *       parsing) skip the retry cycle and go straight to the DLT (I3 — never
 *       retry deterministic 4xx-equivalent failures).</li>
 * </ul>
 *
 * <p>Mirrors the {@code inventory-service} pattern (TASK-MONO-046 learnings).
 *
 * <p>Disabled under the {@code standalone} profile.
 */
@Configuration
@Profile("!standalone")
public class KafkaConsumerConfig {

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> kafkaOperations,
                                          @Value("${wms.notification.kafka.dlt-suffix:.DLT}")
                                                  String dltSuffix) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                (record, exception) -> new org.apache.kafka.common.TopicPartition(
                        record.topic() + dltSuffix,
                        record.partition() < 0 ? 0 : record.partition())
        ) {
            @Override
            protected ProducerRecord<Object, Object> createProducerRecord(
                    org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record,
                    org.apache.kafka.common.TopicPartition topicPartition,
                    org.apache.kafka.common.header.Headers headers,
                    byte[] key,
                    byte[] value) {
                return new ProducerRecord<>(
                        topicPartition.topic(),
                        null,
                        record.key(),
                        record.value(),
                        headers);
            }
        };

        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(Duration.ofSeconds(1).toMillis());
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(Duration.ofSeconds(8).toMillis());
        backOff.setMaxElapsedTime(Duration.ofSeconds(15).toMillis());

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        return handler;
    }
}
