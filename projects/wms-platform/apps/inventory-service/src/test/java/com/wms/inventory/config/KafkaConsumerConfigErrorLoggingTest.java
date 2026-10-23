package com.wms.inventory.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
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
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

/**
 * TASK-BE-651 AC-0 ④ — a listener failure must leave its cause in the log.
 *
 * <p>In the 33rd demo window {@code PickingCancelledConsumer} NPE'd on every
 * {@code wms.outbound.picking.cancelled.v1} record, was retried and dead-lettered, and the log
 * held <em>no</em> exception text. Measured against spring-kafka 3.3.1: with a
 * {@code DeadLetterPublishingRecoverer} wired in, the only per-retry line is the container's
 * {@code INFO "Record in retry and not yet recovered"} (no cause attached), and a successful
 * dead-letter publish logs nothing at INFO or above. So the failure was diagnosable only by
 * reading the DLT's headers.
 *
 * <p>These tests drive the service's <em>real</em> {@link DefaultErrorHandler} bean (the
 * {@code @Bean} method is package-private, called directly — re-building the handler here would
 * test a copy) the way the listener container does, and read what lands on the
 * {@code KafkaConsumerConfig} logger. Only the Kafka producer, consumer and container are mocks.
 */
@ExtendWith(MockitoExtension.class)
class KafkaConsumerConfigErrorLoggingTest {

    private static final String TOPIC = "wms.outbound.picking.cancelled.v1";
    /** Stand-in for the record body: it must never reach the log (payloads are not logged). */
    private static final String VALUE = "{\"payload\":{\"reason\":\"VALUE-MUST-NOT-BE-LOGGED\"}}";

    @Mock KafkaOperations<Object, Object> kafkaOperations;
    @Mock Consumer<Object, Object> kafkaConsumer;
    @Mock MessageListenerContainer container;

    private Logger configLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void attachAppender() {
        configLogger = (Logger) LoggerFactory.getLogger(KafkaConsumerConfig.class);
        appender = new ListAppender<>();
        appender.start();
        configLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        configLogger.detachAppender(appender);
    }

    @Test
    @DisplayName("a retryable failure is logged at WARN with its cause and the record's coordinates")
    void retryableFailureIsLoggedWithCause() {
        DefaultErrorHandler handler = handler();
        ConsumerRecord<Object, Object> record = record(42L);

        Throwable retry = catchThrowable(() -> handler.handleRemaining(
                failure(new NullPointerException("payload.get(\"pickingRequestId\") is null")),
                List.of(record), kafkaConsumer, container));

        // Control: this really was the retry path (seek back, no DLT), not the recover path.
        assertThat(retry).isNotNull();
        verify(kafkaOperations, never()).send(any(ProducerRecord.class));

        ILoggingEvent warn = only(Level.WARN);
        assertThat(warn.getFormattedMessage())
                .contains(TOPIC).contains("42").contains("attempt");
        assertThat(warn.getThrowableProxy()).as("the cause is attached, not just named").isNotNull();
        assertThat(causeChain(warn)).contains(NullPointerException.class.getName());
        assertNoPayloadLogged();
    }

    @Test
    @DisplayName("exhausted retries → dead-lettered to <topic>.DLT and logged at ERROR with the cause")
    void exhaustedRetriesAreDeadLetteredAndLoggedAtError() {
        stubDltSend();
        DefaultErrorHandler handler = handler();
        ConsumerRecord<Object, Object> record = record(7L);
        NullPointerException cause = new NullPointerException("boom");

        // Redeliver the same offset until the handler stops seeking back and recovers.
        int deliveries = 0;
        Throwable thrown;
        do {
            deliveries++;
            thrown = catchThrowable(() -> handler.handleRemaining(
                    failure(cause), List.of(record), kafkaConsumer, container));
        } while (thrown != null && deliveries < 10);
        assertThat(thrown).as("recovered within 10 deliveries").isNull();

        ArgumentCaptor<ProducerRecord<Object, Object>> sent = producerRecordCaptor();
        verify(kafkaOperations, times(1)).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo(TOPIC + ".DLT");

        ILoggingEvent error = only(Level.ERROR);
        assertThat(error.getFormattedMessage()).contains(TOPIC).contains("7").contains(".DLT");
        assertThat(error.getThrowableProxy()).isNotNull();
        assertThat(causeChain(error)).contains(NullPointerException.class.getName());
        assertNoPayloadLogged();
    }

    @Test
    @DisplayName("a non-retryable IllegalArgumentException goes straight to the DLT and is logged at ERROR")
    void nonRetryableFailureIsDeadLetteredAtOnceAndLogged() {
        stubDltSend();
        DefaultErrorHandler handler = handler();

        Throwable thrown = catchThrowable(() -> handler.handleRemaining(
                failure(new IllegalArgumentException("outbound.picking.cancelled missing reservationId")),
                List.of(record(3L)), kafkaConsumer, container));

        assertThat(thrown).isNull();
        verify(kafkaOperations, times(1)).send(any(ProducerRecord.class));
        verify(kafkaConsumer, never()).seek(any(), any(Long.class));

        ILoggingEvent error = only(Level.ERROR);
        assertThat(causeChain(error)).contains(IllegalArgumentException.class.getName());
        assertNoPayloadLogged();
    }

    // ------------------------------------------------------------------ helpers

    private DefaultErrorHandler handler() {
        return new KafkaConsumerConfig().kafkaErrorHandler(kafkaOperations, ".DLT");
    }

    private static ConsumerRecord<Object, Object> record(long offset) {
        return new ConsumerRecord<>(TOPIC, 0, offset, "saga-key", VALUE);
    }

    private static ListenerExecutionFailedException failure(Throwable cause) {
        return new ListenerExecutionFailedException("Listener method threw", cause);
    }

    private ILoggingEvent only(Level level) {
        List<ILoggingEvent> atLevel = appender.list.stream()
                .filter(e -> e.getLevel() == level).toList();
        assertThat(atLevel).as("%s lines on the KafkaConsumerConfig logger", level).isNotEmpty();
        return atLevel.get(atLevel.size() - 1);
    }

    /**
     * Class names along the attached throwable's cause chain. The container hands the handler a
     * {@code ListenerExecutionFailedException} wrapping the listener's own exception; the log line
     * carries the wrapper, so the listener's exception must appear as one of its causes.
     */
    private static List<String> causeChain(ILoggingEvent event) {
        List<String> names = new java.util.ArrayList<>();
        for (IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
            names.add(t.getClassName());
        }
        return names;
    }

    private void assertNoPayloadLogged() {
        assertThat(appender.list).allSatisfy(e ->
                assertThat(e.getFormattedMessage()).doesNotContain("VALUE-MUST-NOT-BE-LOGGED"));
    }

    private void stubDltSend() {
        when(kafkaOperations.send(any(ProducerRecord.class))).thenAnswer(inv -> {
            ProducerRecord<Object, Object> pr = inv.getArgument(0);
            return CompletableFuture.completedFuture(new SendResult<>(pr, null));
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ArgumentCaptor<ProducerRecord<Object, Object>> producerRecordCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(ProducerRecord.class);
    }
}
