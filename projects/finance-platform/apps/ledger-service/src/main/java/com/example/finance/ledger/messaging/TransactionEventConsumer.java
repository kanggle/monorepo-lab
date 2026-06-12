package com.example.finance.ledger.messaging;

import com.example.finance.ledger.application.PostFromTransactionCommand;
import com.example.finance.ledger.application.PostFromTransactionUseCase;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

/**
 * Inbound event adapter (architecture.md § Event consumption). Subscribes the
 * account-service transaction topics on group {@code finance-ledger-v1} and posts
 * the auto-journal entry through {@link PostFromTransactionUseCase}.
 *
 * <ul>
 *   <li>{@code finance.transaction.completed.v1} → Posting Policy entry
 *       (HOLD/RELEASE → no entry, no-op ACK).</li>
 *   <li>{@code finance.transaction.reversed.v1} → swapped REVERSAL entry (F3).</li>
 * </ul>
 *
 * Resilience: {@code @RetryableTopic} (3 attempts → {@code .DLT}); manual ACK; an
 * {@link InvalidEnvelopeException} (malformed/unmappable) is excluded from retry
 * and routed straight to the DLT (no poison loop). <b>Terminal</b> — no
 * re-emission, no outbox; the {@code KafkaTemplate} that {@code @RetryableTopic}
 * uses for retry/DLT records is resilience plumbing only (no domain publish).
 */
@Slf4j
@Component
public class TransactionEventConsumer {

    public static final String TOPIC_COMPLETED = "finance.transaction.completed.v1";
    public static final String TOPIC_REVERSED = "finance.transaction.reversed.v1";
    public static final String GROUP = "finance-ledger-v1";

    private final PostFromTransactionUseCase useCase;
    private final EnvelopeToCommandMapper mapper;

    public TransactionEventConsumer(PostFromTransactionUseCase useCase,
                                    EnvelopeToCommandMapper mapper) {
        this.useCase = useCase;
        this.mapper = mapper;
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".DLT",
            exclude = InvalidEnvelopeException.class
    )
    @KafkaListener(topics = TOPIC_COMPLETED, groupId = GROUP)
    public void onCompleted(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handle(record, ack, TOPIC_COMPLETED,
                () -> mapper.mapCompleted(record.value(), TOPIC_COMPLETED));
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = ".DLT",
            exclude = InvalidEnvelopeException.class
    )
    @KafkaListener(topics = TOPIC_REVERSED, groupId = GROUP)
    public void onReversed(ConsumerRecord<String, String> record, Acknowledgment ack) {
        handle(record, ack, TOPIC_REVERSED,
                () -> mapper.mapReversed(record.value(), TOPIC_REVERSED));
    }

    private void handle(ConsumerRecord<String, String> record, Acknowledgment ack,
                        String topic, java.util.function.Supplier<PostFromTransactionCommand> mapping) {
        try {
            PostFromTransactionCommand cmd = mapping.get();
            useCase.post(cmd);
            ack.acknowledge();
        } catch (InvalidEnvelopeException e) {
            log.error("Invalid transaction envelope on topic={} offset={}; routing to DLT: {}",
                    record.topic(), record.offset(), e.getMessage());
            ack.acknowledge();
            throw e;
        } catch (Exception e) {
            log.error("Failed to process {}: partition={} offset={} error={}",
                    topic, record.partition(), record.offset(), e.getMessage(), e);
            throw new RuntimeException("Failed to process transaction event on " + topic, e);
        }
    }
}
