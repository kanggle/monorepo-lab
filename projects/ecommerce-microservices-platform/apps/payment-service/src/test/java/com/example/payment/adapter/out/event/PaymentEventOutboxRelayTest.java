package com.example.payment.adapter.out.event;

import com.example.messaging.outbox.OutboxPublisher;
import com.example.payment.application.port.out.PaymentMetricRecorder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentEventOutboxRelay 단위 테스트")
class PaymentEventOutboxRelayTest {

    @InjectMocks
    private PaymentEventOutboxRelay relay;

    @Mock
    private OutboxPublisher outboxPublisher;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private PaymentMetricRecorder paymentMetricRecorder;

    @Captor
    private ArgumentCaptor<OutboxPublisher.EventSender> senderCaptor;

    @Test
    @DisplayName("pollAndPublish 호출 시 OutboxPublisher.publishPendingEvents 를 위임 호출한다")
    void pollAndPublish_delegatesToOutboxPublisher() {
        relay.pollAndPublish();

        verify(outboxPublisher).publishPendingEvents(any());
    }

    @Test
    @DisplayName("PaymentCompleted 이벤트는 payment.payment.completed 토픽으로 Kafka 전송된다")
    void sendToKafka_paymentCompleted_sendsToCorrectTopic() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.pollAndPublish();
        verify(outboxPublisher).publishPendingEvents(senderCaptor.capture());

        OutboxPublisher.SendOutcome outcome =
                senderCaptor.getValue().send("PaymentCompleted", "pay-1", "{\"test\":1}");

        assertThat(outcome).isEqualTo(OutboxPublisher.SendOutcome.SUCCESS);
        verify(kafkaTemplate).send("payment.payment.completed", "pay-1", "{\"test\":1}");
    }

    @Test
    @DisplayName("PaymentRefunded 이벤트는 payment.payment.refunded 토픽으로 Kafka 전송된다")
    void sendToKafka_paymentRefunded_sendsToCorrectTopic() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.pollAndPublish();
        verify(outboxPublisher).publishPendingEvents(senderCaptor.capture());

        OutboxPublisher.SendOutcome outcome =
                senderCaptor.getValue().send("PaymentRefunded", "pay-1", "{\"test\":1}");

        assertThat(outcome).isEqualTo(OutboxPublisher.SendOutcome.SUCCESS);
        verify(kafkaTemplate).send("payment.payment.refunded", "pay-1", "{\"test\":1}");
    }

    @Test
    @DisplayName("PaymentRefundStranded 이벤트는 payment.alert.refund.stranded 토픽으로 Kafka 전송된다 (TASK-BE-437)")
    void sendToKafka_paymentRefundStranded_sendsToAlertTopic() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.pollAndPublish();
        verify(outboxPublisher).publishPendingEvents(senderCaptor.capture());

        OutboxPublisher.SendOutcome outcome =
                senderCaptor.getValue().send("PaymentRefundStranded", "pay-1", "{\"test\":1}");

        assertThat(outcome).isEqualTo(OutboxPublisher.SendOutcome.SUCCESS);
        verify(kafkaTemplate).send("payment.alert.refund.stranded", "pay-1", "{\"test\":1}");
    }

    @Test
    @DisplayName("PaymentRefundUnresolved 이벤트는 payment.alert.refund.unresolved 토픽으로 Kafka 전송된다 (TASK-BE-438)")
    void sendToKafka_paymentRefundUnresolved_sendsToAlertTopic() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        relay.pollAndPublish();
        verify(outboxPublisher).publishPendingEvents(senderCaptor.capture());

        OutboxPublisher.SendOutcome outcome =
                senderCaptor.getValue().send("PaymentRefundUnresolved", "pay-1", "{\"test\":1}");

        assertThat(outcome).isEqualTo(OutboxPublisher.SendOutcome.SUCCESS);
        verify(kafkaTemplate).send("payment.alert.refund.unresolved", "pay-1", "{\"test\":1}");
    }

    @Test
    @DisplayName("Kafka transient 실패 시 FAILURE_TRANSIENT 반환 + PaymentMetricRecorder.incrementEventPublishFailure 호출")
    void sendToKafka_transientFailure_returnsTransientAndIncrementsMetric() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka broker unavailable")));

        relay.pollAndPublish();
        verify(outboxPublisher).publishPendingEvents(senderCaptor.capture());

        OutboxPublisher.SendOutcome outcome =
                senderCaptor.getValue().send("PaymentCompleted", "pay-1", "{\"test\":1}");

        assertThat(outcome).isEqualTo(OutboxPublisher.SendOutcome.FAILURE_TRANSIENT);
        verify(paymentMetricRecorder).incrementEventPublishFailure("PaymentCompleted");
    }

    @Test
    @DisplayName("알 수 없는 eventType 은 FAILURE_PERMANENT 로 분류되어 row 가 FAILED 격리 — Kafka 미호출")
    void sendToKafka_unknownEventType_returnsPermanentAndDoesNotCallKafka() {
        relay.pollAndPublish();
        verify(outboxPublisher).publishPendingEvents(senderCaptor.capture());

        OutboxPublisher.SendOutcome outcome =
                senderCaptor.getValue().send("PaymentMutated", "pay-x", "{}");

        assertThat(outcome).isEqualTo(OutboxPublisher.SendOutcome.FAILURE_PERMANENT);
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("자체 resolveTopic 호출 시 알 수 없는 eventType 은 즉시 IllegalArgumentException 던진다")
    void resolveTopic_direct_unknownThrows() {
        assertThatThrownBy(() -> relay.resolveTopic("Bogus"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Bogus");
    }
}
