package com.example.security.infrastructure.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.DeserializationException;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.Map;

@Slf4j
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler errorHandler(KafkaProperties kafkaProperties) {
        // DLQ recoverer needs ByteArraySerializer for values because
        // ErrorHandlingDeserializer preserves raw bytes on deserialization failures.
        Map<String, Object> producerProps = kafkaProperties.buildProducerProperties(null);
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class);
        KafkaTemplate<String, byte[]> dlqTemplate = new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(producerProps));

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dlqTemplate,
                (record, ex) -> {
                    // DeserializationException carries the original raw bytes in record.value()
                    // because ErrorHandlingDeserializer returned null and stashed them on headers;
                    // DeadLetterPublishingRecoverer restores them transparently.
                    String rootCause = ex.getMessage();
                    if (ex.getCause() instanceof DeserializationException de) {
                        rootCause = "DeserializationException: " + de.getMessage();
                    }
                    log.error("Sending to DLQ: topic={}, eventKey={}, error={}",
                            record.topic(), record.key(), rootCause);
                    return new TopicPartition(record.topic() + ".dlq", record.partition());
                }
        );

        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxInterval(30000L);
        backOff.setMaxAttempts(3);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        // Deserialization failures are non-recoverable — bypass retry backoff and go straight to DLQ.
        errorHandler.addNotRetryableExceptions(DeserializationException.class);
        return errorHandler;
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
