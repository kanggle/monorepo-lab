package com.example.account.infrastructure.kafka;

import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.ExponentialBackOff;

/**
 * TASK-MONO-816 AC-2 — the dead letter {@link LoginSucceededConsumer} never had.
 *
 * <p>Until now account-service declared no error-handler bean, so Spring Boot's default handler
 * retried a failing record and then <b>dropped</b> it: no dead-letter topic, no trace of which
 * record was lost. Shape copied from auth-service's {@code KafkaConsumerConfig} (the iam
 * convention):
 * <ul>
 *   <li>3 attempts, exponential back-off 1s × 2 capped at 30s, then the record is published to
 *       {@code <topic>.DLT} (ADR-MONO-085 D1 — the suffix is written out, never left to a
 *       default).</li>
 *   <li>Dead-letter partition {@code -1}: the producer chooses (ADR-MONO-085 D3). The iam compose
 *       files create sources with 3 partitions and their {@code .DLT} topics with 1, so copying the
 *       source partition would aim records from partitions 1 and 2 at partitions that do not
 *       exist.</li>
 *   <li>The DLT publisher is the application's existing {@code KafkaTemplate<String,String>}
 *       (the outbox relay's); this consumer reads plain {@code String} values.</li>
 *   <li>Every failed delivery and the dead-lettering itself are logged with the root cause
 *       ({@link CauseLoggingRetryListener}, the TASK-BE-651 mechanism).</li>
 * </ul>
 *
 * <p>Spring Boot wires a single {@link DefaultErrorHandler} bean into the auto-configured
 * listener container factory.
 */
@Slf4j
@Configuration
public class KafkaConsumerConfig {

    @Bean
    public DefaultErrorHandler kafkaConsumerErrorHandler(KafkaTemplate<String, String> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception ex) ->
                        new TopicPartition(record.topic() + ".DLT", -1));

        ExponentialBackOff backOff = new ExponentialBackOff(1000L, 2.0);
        backOff.setMaxInterval(30_000L);
        backOff.setMaxAttempts(3);

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.setRetryListeners(new CauseLoggingRetryListener());
        return handler;
    }

    /**
     * Copy of {@code TASK-BE-651}'s listener (wms inventory-service). Spring Kafka logs a retried
     * record only as {@code INFO "Record in retry and not yet recovered"} (no exception) and logs
     * nothing at INFO or above when the dead-letter publish succeeds — so without this a poison
     * record leaves no cause in the log.
     *
     * <p>🔴 Never the record key or value: login events carry account identifiers; the DLT already
     * holds the full record for whoever is entitled to read it.
     */
    static final class CauseLoggingRetryListener implements RetryListener {

        @Override
        public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
            log.warn("kafka_listener_failed topic={} partition={} offset={} attempt={} cause={}",
                    record.topic(), record.partition(), record.offset(), deliveryAttempt,
                    NestedExceptionUtils.getMostSpecificCause(ex).toString(), ex);
        }

        @Override
        public void recovered(ConsumerRecord<?, ?> record, Exception ex) {
            log.error("kafka_listener_dead_lettered topic={} partition={} offset={} dlt={} cause={}",
                    record.topic(), record.partition(), record.offset(), record.topic() + ".DLT",
                    NestedExceptionUtils.getMostSpecificCause(ex).toString(), ex);
        }

        @Override
        public void recoveryFailed(ConsumerRecord<?, ?> record, Exception original, Exception failure) {
            log.error("kafka_listener_dead_letter_failed topic={} partition={} offset={} dlt={} cause={} "
                            + "dltFailure={} — the record will be redelivered",
                    record.topic(), record.partition(), record.offset(), record.topic() + ".DLT",
                    NestedExceptionUtils.getMostSpecificCause(original).toString(), failure.toString(),
                    original);
        }
    }
}
