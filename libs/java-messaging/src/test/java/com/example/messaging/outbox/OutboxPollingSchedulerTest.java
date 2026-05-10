package com.example.messaging.outbox;

import com.example.messaging.event.EventSerializationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxPollingScheduler 단위 테스트 — 실패 분류 (transient vs permanent)")
class OutboxPollingSchedulerTest {

    @Mock
    private OutboxPublisher outboxPublisher;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Captor
    private ArgumentCaptor<OutboxPublisher.EventSender> senderCaptor;

    private TestScheduler scheduler;
    private final AtomicReference<String> capturedPermanentEventType = new AtomicReference<>();
    private final AtomicReference<String> capturedTransientEventType = new AtomicReference<>();

    @BeforeEach
    void setUp() {
        capturedPermanentEventType.set(null);
        capturedTransientEventType.set(null);
        scheduler = new TestScheduler(outboxPublisher, kafkaTemplate,
                capturedPermanentEventType, capturedTransientEventType);
    }

    @Test
    @DisplayName("Kafka send 정상 ACK 시 SUCCESS 반환 + onKafkaSendSuccess 호출")
    void sendToKafka_brokerAcked_returnsSuccess() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        scheduler.pollAndPublish();
        verify(outboxPublisher).publishPendingEvents(senderCaptor.capture());

        OutboxPublisher.SendOutcome outcome =
                senderCaptor.getValue().send("Known", "agg-1", "{}");

        assertThat(outcome).isEqualTo(OutboxPublisher.SendOutcome.SUCCESS);
        assertThat(scheduler.successCount).isEqualTo(1);
    }

    @Test
    @DisplayName("Kafka send transient 실패 시 FAILURE_TRANSIENT 반환 + onKafkaSendFailure 호출")
    void sendToKafka_transientException_returnsTransient() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka broker unavailable")));

        scheduler.pollAndPublish();
        verify(outboxPublisher).publishPendingEvents(senderCaptor.capture());

        OutboxPublisher.SendOutcome outcome =
                senderCaptor.getValue().send("Known", "agg-1", "{}");

        assertThat(outcome).isEqualTo(OutboxPublisher.SendOutcome.FAILURE_TRANSIENT);
        assertThat(capturedTransientEventType.get()).isEqualTo("Known");
        assertThat(capturedPermanentEventType.get()).isNull();
    }

    @Test
    @DisplayName("resolveTopic 이 IllegalArgumentException 던지면 FAILURE_PERMANENT 반환 + onPermanentFailure 호출 (kafkaTemplate 미호출)")
    void sendToKafka_unknownEventType_returnsPermanent() {
        scheduler.pollAndPublish();
        verify(outboxPublisher).publishPendingEvents(senderCaptor.capture());

        OutboxPublisher.SendOutcome outcome =
                senderCaptor.getValue().send("Unknown", "agg-1", "{}");

        assertThat(outcome).isEqualTo(OutboxPublisher.SendOutcome.FAILURE_PERMANENT);
        assertThat(capturedPermanentEventType.get()).isEqualTo("Unknown");
        assertThat(capturedTransientEventType.get()).isNull();
        // kafkaTemplate.send 는 호출되지 않아야 한다 — resolveTopic 단계에서 차단됨
        verify(kafkaTemplate, org.mockito.Mockito.never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("EventSerializationException 발생 시 FAILURE_PERMANENT 반환")
    void sendToKafka_serializationException_returnsPermanent() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new EventSerializationException("payload garbled", new RuntimeException()));

        scheduler.pollAndPublish();
        verify(outboxPublisher).publishPendingEvents(senderCaptor.capture());

        OutboxPublisher.SendOutcome outcome =
                senderCaptor.getValue().send("Known", "agg-1", "{}");

        assertThat(outcome).isEqualTo(OutboxPublisher.SendOutcome.FAILURE_PERMANENT);
        assertThat(capturedPermanentEventType.get()).isEqualTo("Known");
    }

    /** Concrete subclass with hook captures + a 2-event resolveTopic. */
    private static final class TestScheduler extends OutboxPollingScheduler {
        private final AtomicReference<String> permanentCapture;
        private final AtomicReference<String> transientCapture;
        int successCount = 0;

        TestScheduler(OutboxPublisher outboxPublisher,
                      KafkaTemplate<String, String> kafkaTemplate,
                      AtomicReference<String> permanentCapture,
                      AtomicReference<String> transientCapture) {
            super(outboxPublisher, kafkaTemplate);
            this.permanentCapture = permanentCapture;
            this.transientCapture = transientCapture;
        }

        @Override
        protected String resolveTopic(String eventType) {
            return switch (eventType) {
                case "Known" -> "known.topic";
                default -> throw new IllegalArgumentException("Unknown event type: " + eventType);
            };
        }

        @Override
        protected void onKafkaSendSuccess(String eventType, String aggregateId) {
            successCount++;
        }

        @Override
        protected void onKafkaSendFailure(String eventType, String aggregateId, Exception e) {
            transientCapture.set(eventType);
        }

        @Override
        protected void onPermanentFailure(String eventType, String aggregateId, Exception e) {
            permanentCapture.set(eventType);
        }
    }
}
