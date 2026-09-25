package com.example.auth.infrastructure.config;

import com.example.auth.infrastructure.messaging.InvalidEventPayloadException;
import com.example.auth.infrastructure.messaging.MissingTenantIdException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * TASK-BE-601 — error handling for auth-service's first Kafka consumer
 * ({@code account.locked}). Spring Boot wires a {@link DefaultErrorHandler} bean into the
 * auto-configured listener container factory.
 *
 * <p>Mirrors security-service's {@code KafkaConsumerConfig} (the iam convention,
 * account-events.md § Consumer Rules — DLQ {@code <topic>.dlq}, 3 attempts):
 * <ul>
 *   <li><b>Retry</b> — exponential back-off 1s × 2, capped at 30s, 3 attempts; then the
 *       record is published to {@code <topic>.dlq}. A failed revoke is therefore never
 *       dropped silently: it is retried, then parked where an operator sees it.</li>
 *   <li><b>No retry</b> — {@link MissingTenantIdException} and
 *       {@link InvalidEventPayloadException}: the bytes will not change, so they go
 *       straight to the DLQ.</li>
 *   <li><b>Metric</b> — {@code outbox.dlq.size{reason}}, the same name and reason tags as
 *       security-service so one alert rule covers both.</li>
 * </ul>
 *
 * <p>Two deliberate differences from security-service:
 * <ul>
 *   <li>The DLQ record is sent WITHOUT a fixed partition (partition {@code -1} → the
 *       producer chooses). The iam compose files create {@code account.locked} with 3
 *       partitions but {@code account.locked.dlq} with 1, so copying the source partition
 *       would fail to publish every record that came from partition 1 or 2.</li>
 *   <li>The DLQ publisher is the application's existing {@code KafkaTemplate<String,String>}
 *       (the outbox relay's). This consumer reads plain {@code String} values with no
 *       {@code ErrorHandlingDeserializer}, so there is no {@code byte[]} value to route and
 *       no second serializer to configure.</li>
 * </ul>
 * The DLQ topic is shared with security-service's group; the recoverer's standard
 * {@code kafka_dlt-original-consumer-group} header tells the two apart.
 */
@Slf4j
@Configuration
public class KafkaConsumerConfig {

    public static final String DLQ_SIZE_METRIC = "outbox.dlq.size";

    @Bean
    public DefaultErrorHandler kafkaConsumerErrorHandler(KafkaTemplate<String, String> kafkaTemplate,
                                                         MeterRegistry meterRegistry) {
        Counter tenantIdMissing = dlqCounter(meterRegistry, "tenant_id_missing");
        Counter invalidPayload = dlqCounter(meterRegistry, "invalid_payload");
        Counter other = dlqCounter(meterRegistry, "other");

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception ex) -> {
                    String reason;
                    if (hasCause(ex, MissingTenantIdException.class)) {
                        tenantIdMissing.increment();
                        reason = "tenant_id_missing";
                    } else if (hasCause(ex, InvalidEventPayloadException.class)) {
                        invalidPayload.increment();
                        reason = "invalid_payload";
                    } else {
                        other.increment();
                        reason = "other";
                    }
                    log.error("Sending to DLQ: topic={} partition={} offset={} key={} reason={} error={}",
                            record.topic(), record.partition(), record.offset(), record.key(),
                            reason, ex.getMessage());
                    return new TopicPartition(record.topic() + ".dlq", -1);
                });

        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxInterval(30_000L);
        backOff.setMaxAttempts(3);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(
                MissingTenantIdException.class, InvalidEventPayloadException.class);
        return errorHandler;
    }

    private static Counter dlqCounter(MeterRegistry registry, String reason) {
        return Counter.builder(DLQ_SIZE_METRIC)
                .description("Number of messages routed to DLQ")
                .tag("reason", reason)
                .register(registry);
    }

    static boolean hasCause(Throwable ex, Class<? extends Throwable> target) {
        Throwable current = ex;
        while (current != null) {
            if (target.isInstance(current)) {
                return true;
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }
}
