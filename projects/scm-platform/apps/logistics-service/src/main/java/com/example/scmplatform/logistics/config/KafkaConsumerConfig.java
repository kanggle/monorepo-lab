package com.example.scmplatform.logistics.config;

import com.example.scmplatform.logistics.adapter.inbound.messaging.NonRetryableConsumerException;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.ExponentialBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka consumer wiring for the {@code outbound.shipping.confirmed} seam (group
 * {@code scm-logistics-v1}, manual ack, earliest, read_committed) plus the retry/DLT error handler
 * (external-integrations.md §4; subscriptions contract § Retry + DLT). TASK-SCM-BE-044 adds the
 * {@link DefaultErrorHandler} + DLT publisher onto the BE-042 scaffold.
 *
 * <p><b>Retry/DLT policy.</b> A retryable failure (DB/infra fault thrown by the consume TX) is
 * retried with exponential backoff {@code [1s, 2s, 4s]} (3 retries), then routed to the DLT
 * {@code wms.outbound.shipping.confirmed.v1.DLT}. A {@link NonRetryableConsumerException} (malformed
 * envelope) is <b>non-retryable</b> → routed to the DLT immediately (no backoff). A <b>vendor
 * dispatch failure never reaches here</b> — it is swallowed into {@code DISPATCH_FAILED} and ack'd
 * by the consumer (S5, Cat C).
 *
 * <p>Works with {@code AckMode.MANUAL}: the success path acks explicitly; after a record is recovered
 * to the DLT the {@code DefaultErrorHandler} ({@code ackAfterHandle = true}) has the container commit
 * the recovered offset, so the seam is never blocked.
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Bean
    public ConsumerFactory<String, String> consumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "scm-logistics-v1");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        props.put(ConsumerConfig.ISOLATION_LEVEL_CONFIG, "read_committed");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * String producer used ONLY by the {@link DeadLetterPublishingRecoverer} to forward failed
     * records (raw value + headers) to the DLT. logistics-service publishes no domain events in
     * Phase 1 (no outbox — ADR-053 §D2); this is a DLT-only producer.
     */
    @Bean
    public ProducerFactory<String, String> dltProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        return new DefaultKafkaProducerFactory<>(props);
    }

    @Bean
    public KafkaTemplate<String, String> dltKafkaTemplate(ProducerFactory<String, String> dltProducerFactory) {
        return new KafkaTemplate<>(dltProducerFactory);
    }

    /**
     * Retry [1s,2s,4s] → DLT, with malformed envelopes non-retryable (immediate DLT). The DLT topic
     * is the source topic + {@code .DLT}; partition {@code -1} lets the producer choose (robust to a
     * DLT with a different partition count than the source).
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, String> dltKafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                dltKafkaTemplate,
                (record, ex) -> new TopicPartition(record.topic() + ".DLT", -1));

        // Exponential [1s, 2s, 4s] → then DLT. maxElapsedTime = 7000 stops the sequence after
        // exactly 3 retries (1000 + 2000 + 4000): the 4th nextBackOff() sees elapsed == 7000 and
        // returns STOP → recover to DLT. (Spring 6.2 has no ExponentialBackOffWithMaxRetries.)
        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(1000L);
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(4000L);
        backOff.setMaxElapsedTime(7000L);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        // Malformed envelope → straight to DLT, no retry (subscriptions contract § Retry + DLT).
        handler.addNotRetryableExceptions(NonRetryableConsumerException.class);
        return handler;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            DefaultErrorHandler kafkaErrorHandler) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}
