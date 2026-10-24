package com.example.account.infrastructure.kafka;

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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-MONO-816 AC-2 / AC-3 — a poison {@code auth.login.succeeded} record is dead-lettered to
 * {@code auth.login.succeeded.DLT} (ADR-MONO-085 D1, partition left to the producer per D3) and the
 * log says why. Before this task account-service had no error-handler bean at all: Spring Boot's
 * default handler retried the record and then dropped it.
 *
 * <p>Drives the service's real {@link DefaultErrorHandler} bean method; only the Kafka producer,
 * consumer and container are mocks.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaConsumerConfig — account-service 데드레터 (TASK-MONO-816)")
class KafkaConsumerConfigTest {

    private static final String TOPIC = "auth.login.succeeded";
    /** Stand-in for the record body: it must never reach the log. */
    private static final String VALUE = "{\"payload\":{\"accountId\":\"VALUE-MUST-NOT-BE-LOGGED\"}}";

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
    @DisplayName("재시도를 다 쓴 poison 레코드 → <topic>.DLT (파티션 지정 없음) 로 가고 원인이 ERROR 로 남는다")
    void poisonRecordLandsOnDltAndCauseIsLogged() {
        when(kafkaTemplate.send(any(ProducerRecord.class))).thenAnswer(inv -> {
            ProducerRecord<String, String> pr = inv.getArgument(0);
            return CompletableFuture.completedFuture(new SendResult<>(pr, null));
        });
        DefaultErrorHandler handler = new KafkaConsumerConfig().kafkaConsumerErrorHandler(kafkaTemplate);
        ConsumerRecord<Object, Object> record = new ConsumerRecord<>(TOPIC, 2, 7L, "acc-key", VALUE);
        IllegalStateException cause = new IllegalStateException("account row locked");

        // Redeliver the same offset until the handler stops seeking back and recovers.
        int deliveries = 0;
        Throwable thrown;
        do {
            deliveries++;
            thrown = catchThrowable(() -> handler.handleRemaining(
                    new ListenerExecutionFailedException("Listener method threw", cause),
                    List.of(record), kafkaConsumer, container));
        } while (thrown != null && deliveries < 10);
        assertThat(thrown).as("recovered within 10 deliveries").isNull();

        @SuppressWarnings({"unchecked", "rawtypes"})
        ArgumentCaptor<ProducerRecord<String, String>> sent = (ArgumentCaptor) ArgumentCaptor.forClass(ProducerRecord.class);
        verify(kafkaTemplate, times(1)).send(sent.capture());
        assertThat(sent.getValue().topic()).isEqualTo(TOPIC + ".DLT");
        assertThat(sent.getValue().partition())
                .as("partition -1 → null on the ProducerRecord: the producer chooses (ADR-MONO-085 D3)")
                .isNull();

        List<ILoggingEvent> errors = appender.list.stream().filter(e -> e.getLevel() == Level.ERROR).toList();
        assertThat(errors).hasSize(1);
        ILoggingEvent error = errors.get(0);
        assertThat(error.getFormattedMessage())
                .contains(TOPIC).contains("partition=2").contains("offset=7").contains(TOPIC + ".DLT")
                .contains("IllegalStateException").contains("account row locked");
        assertThat(causeChain(error)).contains(IllegalStateException.class.getName());
        assertThat(appender.list).allSatisfy(e -> assertThat(e.getFormattedMessage())
                .doesNotContain("VALUE-MUST-NOT-BE-LOGGED").doesNotContain("acc-key"));
    }

    private static List<String> causeChain(ILoggingEvent event) {
        List<String> names = new ArrayList<>();
        for (IThrowableProxy t = event.getThrowableProxy(); t != null; t = t.getCause()) {
            names.add(t.getClassName());
        }
        return names;
    }
}
