package com.example.security.infrastructure.kafka;

import com.example.security.consumer.MissingTenantIdException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Serializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.kafka.support.serializer.DelegatingByTypeSerializer;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.Map;

@Slf4j
@Configuration
public class KafkaConsumerConfig {

    /**
     * Metric name: {@code outbox.dlq.size} — incremented every time a message is
     * routed to a DLQ topic (either from deserialization failure or from
     * {@code tenant_id} validation failure in the consumer layer).
     *
     * <p>Tag: {@code reason} distinguishes deserialization failures from tenant
     * validation failures for operational alerting.</p>
     */
    public static final String DLQ_SIZE_METRIC = "outbox.dlq.size";

    @Bean
    public DefaultErrorHandler errorHandler(KafkaProperties kafkaProperties,
                                            MeterRegistry meterRegistry) {
        // DLQ recoverer must handle two value types:
        //   - byte[]  : deserialization-failed records where ErrorHandlingDeserializer
        //               preserved the raw bytes and DLPR restores them transparently.
        //   - String  : records that deserialized successfully but failed downstream
        //               (e.g. MissingTenantIdException) — value is the decoded String.
        // DelegatingByTypeSerializer dispatches to the correct serializer at runtime,
        // avoiding the ClassCastException that occurred when ByteArraySerializer-only
        // received a String value (TASK-MONO-046-4).
        Map<Class<?>, Serializer<?>> typeMap = Map.of(
                byte[].class, new ByteArraySerializer(),
                String.class, new StringSerializer()
        );
        Serializer<Object> dlqValueSerializer = new DelegatingByTypeSerializer(typeMap);

        Map<String, Object> producerProps = kafkaProperties.buildProducerProperties(null);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        // VALUE_SERIALIZER_CLASS_CONFIG is intentionally omitted — the serializer instance
        // is injected directly into DefaultKafkaProducerFactory via the 3-arg constructor.
        KafkaTemplate<String, Object> dlqTemplate = new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(producerProps, new StringSerializer(), dlqValueSerializer));
        // TASK-MONO-046-8: surface DLPR publish failures (e.g. serializer mismatch
        // for byte[] DLPR path) instead of swallowing them silently.
        dlqTemplate.setProducerListener(new org.springframework.kafka.support.ProducerListener<>() {
            @Override
            public void onError(org.apache.kafka.clients.producer.ProducerRecord<String, Object> producerRecord,
                                org.apache.kafka.clients.producer.RecordMetadata recordMetadata,
                                Exception exception) {
                log.error("DLQ publish FAILED for topic={} valueClass={}: {}",
                        producerRecord.topic(),
                        producerRecord.value() == null ? "null" : producerRecord.value().getClass().getName(),
                        exception.getMessage(), exception);
            }
        });

        // Pre-create counters per reason so they are visible in Prometheus from startup.
        Counter deserializationDlqCounter = Counter.builder(DLQ_SIZE_METRIC)
                .description("Number of messages routed to DLQ")
                .tag("reason", "deserialization_failure")
                .register(meterRegistry);
        Counter tenantIdMissingDlqCounter = Counter.builder(DLQ_SIZE_METRIC)
                .description("Number of messages routed to DLQ")
                .tag("reason", "tenant_id_missing")
                .register(meterRegistry);
        Counter otherDlqCounter = Counter.builder(DLQ_SIZE_METRIC)
                .description("Number of messages routed to DLQ")
                .tag("reason", "other")
                .register(meterRegistry);

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dlqTemplate,
                (record, ex) -> {
                    // DeserializationException carries the original raw bytes in record.value()
                    // because ErrorHandlingDeserializer returned null and stashed them on headers;
                    // DeadLetterPublishingRecoverer restores them transparently.
                    String rootCause = ex.getMessage();
                    if (containsCause(ex, DeserializationException.class)) {
                        rootCause = "DeserializationException: " + ex.getMessage();
                        deserializationDlqCounter.increment();
                    } else if (containsCause(ex, MissingTenantIdException.class)) {
                        tenantIdMissingDlqCounter.increment();
                    } else {
                        otherDlqCounter.increment();
                    }
                    Object value = record.value();
                    log.warn("Sending to DLQ: topic={}, eventKey={}, valueClass={}, error={}",
                            record.topic(), record.key(),
                            value == null ? "null" : value.getClass().getName(),
                            rootCause);
                    return new TopicPartition(record.topic() + ".dlq", record.partition());
                }
        );


        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxInterval(30000L);
        backOff.setMaxAttempts(3);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        // Deserialization failures are non-recoverable — bypass retry backoff and go straight to DLQ.
        errorHandler.addNotRetryableExceptions(DeserializationException.class);
        // tenant_id missing is also non-recoverable — the producer must fix its payload.
        errorHandler.addNotRetryableExceptions(MissingTenantIdException.class);
        return errorHandler;
    }

    private static boolean containsCause(Throwable ex, Class<? extends Throwable> target) {
        Throwable cur = ex;
        while (cur != null) {
            if (target.isInstance(cur)) {
                return true;
            }
            cur = cur.getCause() == cur ? null : cur.getCause();
        }
        return false;
    }

    /**
     * Exposes the Kafka {@link AdminClient} as a bean so metrics/observability
     * components (e.g. {@code SecurityMetricsConfig}) can inject it without
     * constructing their own, and tests can swap in a mock.
     */
    @Bean(destroyMethod = "close")
    public AdminClient kafkaAdminClient(KafkaProperties kafkaProperties) {
        return AdminClient.create(kafkaProperties.buildAdminProperties(null));
    }
}
