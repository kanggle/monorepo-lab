package com.example.scmplatform.logistics.config;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.read.ListAppender;
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
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.SendResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-816 AC-1 / AC-3 — a listener failure must leave its cause in the log.
 *
 * <p>Same test as wms inventory-service's {@code KafkaConsumerConfigErrorLoggingTest} (TASK-BE-651):
 * with a {@code DeadLetterPublishingRecoverer} wired in, spring-kafka 3.3 logs each retry only as the
 * container's {@code INFO "Record in retry and not yet recovered"} (no cause attached) and logs
 * nothing at INFO or above when the dead-letter publish succeeds. Drives the service's real
 * {@link DefaultErrorHandler} bean; only the Kafka producer, consumer and container are mocks.
 */
@ExtendWith(MockitoExtension.class)
class KafkaConsumerConfigErrorLoggingTest {

    private static final String TOPIC = "wms.outbound.shipping.confirmed.v1";
    /** Stand-in for the record body: it must never reach the log (payloads are not logged). */
    private static final String VALUE = "{\"payload\":{\"reason\":\"VALUE-MUST-NOT-BE-LOGGED\"}}";

    @Mock KafkaTemplate<String, String> kafkaTemplate;
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
        DefaultErrorHandler handler = new KafkaConsumerConfig().kafkaErrorHandler(kafkaTemplate);
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(TOPIC, 1, 42L, "k", VALUE);

        Throwable retry = catchThrowable(() -> handler.handleRemaining(
                failure(new IllegalStateException("shipment row locked")),
                List.of(record), kafkaConsumer, container));

        // Control: this really was the retry path (seek back, no DLT), not the recover path.
        assertThat(retry).isNotNull();
        verify(kafkaTemplate, never()).send(any(ProducerRecord.class));

        ILoggingEvent warn = only(Level.WARN);
        assertThat(warn.getFormattedMessage())
                .contains(TOPIC).contains("partition=1").contains("offset=42").contains("attempt")
                .contains("IllegalStateException").contains("shipment row locked");
        assertThat(causeChain(warn)).contains(IllegalStateException.class.getName());
        assertNoPayloadLogged();
    }

    @Test
    @DisplayName("exhausted retries → dead-lettered to <topic>.DLT and logged at ERROR with the cause")
    void exhaustedRetriesAreDeadLetteredAndLoggedAtError() {
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenAnswer(inv -> {
            ProducerRecord<String, String> pr = inv.getArgument(0);
            return CompletableFuture.completedFuture(new SendResult<>(pr, null));
        });
        DefaultErrorHandler handler = new KafkaConsumerConfig().kafkaErrorHandler(kafkaTemplate);
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(TOPIC, 0, 7L, "k", VALUE);
        IllegalStateException cause = new IllegalStateException("boom");

        int deliveries = 0;
        Throwable thrown;
        do {
            deliveries++;
            thrown = catchThrowable(() -> handler.handleRemaining(
                    failure(cause), List.of(record), kafkaConsumer, container));
        } while (thrown != null && deliveries < 10);
        assertThat(thrown).as("recovered within 10 deliveries").isNull();

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<ProducerRecord<String, String>> sent = (ArgumentCaptor) ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(1)).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo(TOPIC + ".DLT");

        ILoggingEvent error = only(Level.ERROR);
        assertThat(error.getFormattedMessage())
                .contains(TOPIC).contains("offset=7").contains(".DLT")
                .contains("IllegalStateException").contains("boom");
        assertThat(causeChain(error)).contains(IllegalStateException.class.getName());
        assertNoPayloadLogged();
    }

    private static ListenerExecutionFailedException failure(Throwable cause) {
        return new ListenerExecutionFailedException("Listener method threw", cause);
    }

    private ILoggingEvent only(Level level) {
        List<ILoggingEvent> atLevel = appender.list.stream().filter(e -> e.getLevel() == level).toList();
        assertThat(atLevel).as("%s lines on the KafkaConsumerConfig logger", level).isNotEmpty();
        return atLevel.get(atLevel.size() - 1);
    }

    private static List<String> causeChain(ILoggingEvent event) {
        List<String> names = new ArrayList<>();
        for (IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
            names.add(t.getClassName());
        }
        return names;
    }

    private void assertNoPayloadLogged() {
        assertThat(appender.list).allSatisfy(e ->
                assertThat(e.getFormattedMessage()).doesNotContain("VALUE-MUST-NOT-BE-LOGGED"));
    }
}
