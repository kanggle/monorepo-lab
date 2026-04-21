package com.example.payment.adapter.out.event;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.example.payment.application.event.PaymentCompletedEvent;
import com.example.payment.application.event.PaymentRefundedEvent;
import com.example.payment.application.port.out.PaymentMetricRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.KafkaException;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("KafkaPaymentEventPublisher 단위 테스트")
class KafkaPaymentEventPublisherTest {

    private static final String TOPIC_COMPLETED = "payment.payment.completed";
    private static final String TOPIC_REFUNDED = "payment.payment.refunded";

    private KafkaPaymentEventPublisher publisher;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Mock
    private PaymentMetricRecorder paymentMetricRecorder;

    private ListAppender<ILoggingEvent> listAppender;
    private Logger logger;

    @BeforeEach
    void setUp() {
        publisher = new KafkaPaymentEventPublisher(
                TOPIC_COMPLETED, TOPIC_REFUNDED, kafkaTemplate, paymentMetricRecorder
        );
        logger = (Logger) LoggerFactory.getLogger(KafkaPaymentEventPublisher.class);
        listAppender = new ListAppender<>();
        listAppender.start();
        logger.addAppender(listAppender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(listAppender);
    }

    private PaymentCompletedEvent sampleCompletedEvent() {
        return new PaymentCompletedEvent(
                UUID.randomUUID().toString(),
                "PaymentCompleted",
                Instant.now().toString(),
                "payment-service",
                new PaymentCompletedEvent.Payload("pay-1", "order-1", "user-1", 30000L, Instant.now().toString())
        );
    }

    private PaymentRefundedEvent sampleRefundedEvent() {
        return new PaymentRefundedEvent(
                UUID.randomUUID().toString(),
                "PaymentRefunded",
                Instant.now().toString(),
                "payment-service",
                new PaymentRefundedEvent.Payload("pay-1", "order-1", "user-1", 30000L, Instant.now().toString())
        );
    }

    @Test
    @DisplayName("PaymentCompleted 이벤트 발행 실패 시 event_publish_failure_total 메트릭이 증가한다")
    void publishPaymentCompleted_kafkaFailure_incrementsEventPublishFailureMetric() {
        given(kafkaTemplate.send(any(), any(), any())).willThrow(new KafkaException("Kafka broker unavailable"));

        publisher.publishPaymentCompleted(sampleCompletedEvent());

        verify(paymentMetricRecorder).incrementEventPublishFailure("PaymentCompleted");
    }

    @Test
    @DisplayName("PaymentCompleted 이벤트 발행 실패 시 ERROR 레벨로 로그가 기록된다")
    void publishPaymentCompleted_kafkaFailure_logsAtErrorLevel() {
        given(kafkaTemplate.send(any(), any(), any())).willThrow(new KafkaException("Kafka broker unavailable"));

        publisher.publishPaymentCompleted(sampleCompletedEvent());

        assertThat(listAppender.list)
                .filteredOn(e -> e.getLevel() == Level.ERROR)
                .anyMatch(e -> e.getFormattedMessage().contains("Event publishing failed")
                        && e.getFormattedMessage().contains("PaymentCompleted"));
    }

    @Test
    @DisplayName("PaymentRefunded 이벤트 발행 실패 시 event_publish_failure_total 메트릭이 증가한다")
    void publishPaymentRefunded_kafkaFailure_incrementsEventPublishFailureMetric() {
        given(kafkaTemplate.send(any(), any(), any())).willThrow(new KafkaException("Kafka broker unavailable"));

        publisher.publishPaymentRefunded(sampleRefundedEvent());

        verify(paymentMetricRecorder).incrementEventPublishFailure("PaymentRefunded");
    }

    @Test
    @DisplayName("PaymentRefunded 이벤트 발행 실패 시 ERROR 레벨로 로그가 기록된다")
    void publishPaymentRefunded_kafkaFailure_logsAtErrorLevel() {
        given(kafkaTemplate.send(any(), any(), any())).willThrow(new KafkaException("Kafka broker unavailable"));

        publisher.publishPaymentRefunded(sampleRefundedEvent());

        assertThat(listAppender.list)
                .filteredOn(e -> e.getLevel() == Level.ERROR)
                .anyMatch(e -> e.getFormattedMessage().contains("Event publishing failed")
                        && e.getFormattedMessage().contains("PaymentRefunded"));
    }
}
