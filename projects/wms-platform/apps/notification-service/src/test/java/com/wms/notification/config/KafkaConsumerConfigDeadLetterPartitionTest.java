package com.wms.notification.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaOperations;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.kafka.support.SendResult;

/**
 * TASK-BE-657 — which partition a dead letter is sent to. Pre-created wms {@code .DLT} topics have 1 partition and
 * sources have 3 ({@code scripts/kafka-create-topics.sh}). Before the ticket the resolver named the source partition,
 * so spring-kafka 3.3.1's {@code DeadLetterPublishingRecoverer.checkPartition} looked the dead-letter topic up and
 * logged «Destination resolver returned non-existent partition …» for every record from partition 1 or 2 (34th demo
 * window, TASK-MONO-813 AC-3). The resolver now returns {@code -1}: no lookup, no warning, the producer picks by key.
 *
 * <p>Drives the service's real {@link DefaultErrorHandler} bean with a non-retryable failure (recovered at once),
 * as {@code KafkaConsumerConfigErrorLoggingTest} does. Only the producer, consumer and container are mocks.
 */
@ExtendWith(MockitoExtension.class)
class KafkaConsumerConfigDeadLetterPartitionTest {

    private static final String TOPIC = "wms.test.source.v1";
    private static final String VALUE = "{\"eventId\":\"e-1\"}";

    @Mock KafkaOperations<Object, Object> kafkaOperations;
    @Mock Consumer<Object, Object> kafkaConsumer;
    @Mock MessageListenerContainer container;

    private Logger recovererLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        when(kafkaOperations.send(any(ProducerRecord.class))).thenAnswer(inv -> {
            ProducerRecord<Object, Object> pr = inv.getArgument(0);
            return CompletableFuture.completedFuture(new SendResult<>(pr, null));
        });
        recovererLogger = (Logger) LoggerFactory.getLogger(DeadLetterPublishingRecoverer.class);
        appender = new ListAppender<>();
        appender.start();
        recovererLogger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        recovererLogger.detachAppender(appender);
    }

    @Test
    @DisplayName("🔴 a record from source partition 2 is dead-lettered with no partition (producer picks by key) — no lookup, no «non-existent partition» warning")
    void sourcePartition2_noPartitionNoLookupNoWarning() {
        // the pre-created .DLT has ONE partition
        lenient().when(kafkaConsumer.partitionsFor(anyString(), any(Duration.class)))
                .thenReturn(List.of(new PartitionInfo(TOPIC + ".DLT", 0, null, null, null)));

        ProducerRecord<Object, Object> sent = deadLetter(2);

        assertThat(sent.topic()).isEqualTo(TOPIC + ".DLT");
        assertThat(sent.partition()).as("producer chooses by key").isNull();
        verify(kafkaConsumer, never()).partitionsFor(anyString(), any(Duration.class));
        assertThat(appender.list).filteredOn(e -> e.getLevel() == Level.WARN)
                .noneSatisfy(e -> assertThat(e.getFormattedMessage()).contains("non-existent partition"));
    }

    @Test
    @DisplayName("🔴 dead-letter topic metadata unavailable → still no partition (never the source partition number)")
    void metadataUnavailable_stillNoPartition() {
        // checkPartition returns the resolver's partition unchanged when partitionsFor answers null
        lenient().when(kafkaConsumer.partitionsFor(anyString(), any(Duration.class))).thenReturn(null);

        assertThat(deadLetter(2).partition()).isNull();
    }

    @Test
    @DisplayName("control: key, value and the original-coordinate headers are forwarded unchanged")
    void keyValueAndHeadersForwarded() {
        ProducerRecord<Object, Object> sent = deadLetter(1);

        assertThat(sent.key()).isEqualTo("saga-key");
        assertThat(sent.value()).isEqualTo(VALUE);
        assertThat(header(sent, KafkaHeaders.DLT_ORIGINAL_TOPIC)).isEqualTo(TOPIC);
        assertThat(ByteBuffer.wrap(sent.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_PARTITION).value()).getInt())
                .isEqualTo(1);
        assertThat(ByteBuffer.wrap(sent.headers().lastHeader(KafkaHeaders.DLT_ORIGINAL_OFFSET).value()).getLong())
                .isEqualTo(11L);
        // the container hands the handler the listener's exception wrapped; the cause header names the listener's own
        assertThat(header(sent, KafkaHeaders.DLT_EXCEPTION_FQCN)).isEqualTo(ListenerExecutionFailedException.class.getName());
        assertThat(header(sent, KafkaHeaders.DLT_EXCEPTION_CAUSE_FQCN)).isEqualTo(IllegalArgumentException.class.getName());
    }

    // ------------------------------------------------------------------ helpers

    private ProducerRecord<Object, Object> deadLetter(int sourcePartition) {
        DefaultErrorHandler handler = new KafkaConsumerConfig().kafkaErrorHandler(kafkaOperations, ".DLT");
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(TOPIC, sourcePartition, 11L, "saga-key", VALUE);

        handler.handleRemaining(
                new ListenerExecutionFailedException("Listener method threw",
                        new IllegalArgumentException("envelope missing eventId")),
                List.of(record), kafkaConsumer, container);

        ArgumentCaptor<ProducerRecord<Object, Object>> sent = captor();
        verify(kafkaOperations, times(1)).send(sent.capture());
        return sent.getValue();
    }

    private static String header(ProducerRecord<Object, Object> pr, String name) {
        Header h = pr.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<ProducerRecord<Object, Object>> captor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(ProducerRecord.class);
    }
}
