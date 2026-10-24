package com.example.order.infrastructure.config;

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
import org.springframework.kafka.listener.CommonErrorHandler;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-816 AC-0 / AC-3 — the «resolver logs only {@code ex.getMessage()}» shape.
 *
 * <p>The container hands the recoverer a {@link ListenerExecutionFailedException} wrapping the
 * listener's own exception, and its message names only the wrapper («Listener method … threw
 * exception»), never the wrapped cause. So a resolver that logs {@code ex.getMessage()} tells the
 * operator that <em>something</em> failed, not what. This test drives the service's real error
 * handler bean to the dead-letter and reads what the destination resolver logged.
 */
@ExtendWith(MockitoExtension.class)
class KafkaConsumerConfigErrorLoggingTest {

    private static final String TOPIC = "order.payment.completed.v1";

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
    @DisplayName("dead-lettering logs the listener's own exception (class + message) with topic/partition/offset")
    void deadLetterLogNamesTheRootCause() {
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenAnswer(inv -> {
            ProducerRecord<String, String> pr = inv.getArgument(0);
            return CompletableFuture.completedFuture(new SendResult<>(pr, null));
        });
        CommonErrorHandler common = new KafkaConsumerConfig().kafkaErrorHandler(kafkaTemplate);
        DefaultErrorHandler handler = (DefaultErrorHandler) common;
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(TOPIC, 2, 9L, "k", "VALUE-MUST-NOT-BE-LOGGED");
        IllegalStateException cause = new IllegalStateException("order 42 not found");

        int deliveries = 0;
        Throwable thrown;
        do {
            deliveries++;
            thrown = catchThrowable(() -> handler.handleRemaining(
                    new ListenerExecutionFailedException("Listener method threw exception", cause),
                    List.of(record), kafkaConsumer, container));
        } while (thrown != null && deliveries < 10);
        assertThat(thrown).as("recovered within 10 deliveries").isNull();

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<ProducerRecord<String, String>> sent = (ArgumentCaptor) ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(1)).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo(TOPIC + ".DLT");

        List<ILoggingEvent> errors = appender.list.stream().filter(e -> e.getLevel() == Level.ERROR).toList();
        assertThat(errors).as("one ERROR line for the dead-lettering").hasSize(1);
        ILoggingEvent error = errors.get(0);
        assertThat(error.getFormattedMessage())
                .contains(TOPIC).contains("partition=2").contains("offset=9")
                .contains("IllegalStateException").contains("order 42 not found");
        assertThat(causeChain(error)).contains(IllegalStateException.class.getName());
        assertThat(appender.list).allSatisfy(e ->
                assertThat(e.getFormattedMessage()).doesNotContain("VALUE-MUST-NOT-BE-LOGGED"));
    }

    private static List<String> causeChain(ILoggingEvent event) {
        List<String> names = new ArrayList<>();
        for (IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
            names.add(t.getClassName());
        }
        return names;
    }
}
