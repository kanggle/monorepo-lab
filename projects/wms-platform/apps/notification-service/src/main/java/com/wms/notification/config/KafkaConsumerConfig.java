package com.wms.notification.config;

import java.time.Duration;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
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
 *   <li>Every failed delivery is logged at WARN and every dead-lettering at ERROR, each with
 *       its cause ({@link CauseLoggingRetryListener}, TASK-BE-651 / TASK-MONO-816).</li>
 * </ul>
 *
 * <p>Mirrors the {@code inventory-service} pattern (TASK-MONO-046 learnings).
 *
 * <p>Disabled under the {@code standalone} profile.
 */
@Configuration
@Profile("!standalone")
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    DefaultErrorHandler kafkaErrorHandler(KafkaOperations<Object, Object> kafkaOperations,
                                          @Value("${wms.notification.kafka.dlt-suffix:.DLT}")
                                                  String dltSuffix) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaOperations,
                // TASK-BE-657: partition -1 — the producer picks by key (event-driven-policy.md § DLQ Policy).
                // The pre-created .DLT has 1 partition and the source 3; naming the source partition made
                // spring-kafka look the topic up and warn «non-existent partition» for records from p1/p2.
                (record, exception) -> new org.apache.kafka.common.TopicPartition(record.topic() + dltSuffix, -1));

        ExponentialBackOff backOff = new ExponentialBackOff();
        backOff.setInitialInterval(Duration.ofSeconds(1).toMillis());
        backOff.setMultiplier(2.0);
        backOff.setMaxInterval(Duration.ofSeconds(8).toMillis());
        backOff.setMaxElapsedTime(Duration.ofSeconds(15).toMillis());

        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, backOff);
        handler.addNotRetryableExceptions(IllegalArgumentException.class);
        handler.setRetryListeners(new CauseLoggingRetryListener(dltSuffix));
        return handler;
    }

    /**
     * TASK-MONO-816 (copy of TASK-BE-651's listener in inventory-service): without it a failing
     * record leaves <em>no cause</em> in the log. With a {@link DeadLetterPublishingRecoverer}
     * wired in, spring-kafka 3.3 logs each retry only as the container's
     * {@code INFO "Record in retry and not yet recovered"} (no exception attached) and logs nothing
     * at INFO or above when the dead-letter publish succeeds.
     *
     * <p>Logs topic-partition@offset, the attempt number and the exception (with stack trace).
     * 🔴 Never the record key or value: payloads can carry business data, and the DLT already
     * holds the full record for whoever is entitled to read it.
     */
    static final class CauseLoggingRetryListener implements RetryListener {

        private final String dltSuffix;

        CauseLoggingRetryListener(String dltSuffix) {
            this.dltSuffix = dltSuffix;
        }

        @Override
        public void failedDelivery(ConsumerRecord<?, ?> record, Exception ex, int deliveryAttempt) {
            log.warn("kafka_listener_failed topic={} partition={} offset={} attempt={} cause={}",
                    record.topic(), record.partition(), record.offset(), deliveryAttempt,
                    rootCause(ex).toString(), ex);
        }

        @Override
        public void recovered(ConsumerRecord<?, ?> record, Exception ex) {
            log.error("kafka_listener_dead_lettered topic={} partition={} offset={} dlt={} cause={}",
                    record.topic(), record.partition(), record.offset(), record.topic() + dltSuffix,
                    rootCause(ex).toString(), ex);
        }

        @Override
        public void recoveryFailed(ConsumerRecord<?, ?> record, Exception original, Exception failure) {
            log.error("kafka_listener_dead_letter_failed topic={} partition={} offset={} dlt={} cause={} "
                            + "dltFailure={} — the record will be redelivered",
                    record.topic(), record.partition(), record.offset(), record.topic() + dltSuffix,
                    rootCause(original).toString(), failure.toString(), original);
        }

        /** The listener's own exception, unwrapped from {@code ListenerExecutionFailedException}. */
        private static Throwable rootCause(Throwable ex) {
            Throwable t = ex;
            while (t.getCause() != null && t.getCause() != t) {
                t = t.getCause();
            }
            return t;
        }
    }
}
