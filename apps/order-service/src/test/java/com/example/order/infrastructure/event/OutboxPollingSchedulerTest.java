package com.example.order.infrastructure.event;

import com.example.order.application.port.OrderMetricsPort;
import com.example.messaging.outbox.OutboxPublisher;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OutboxPollingScheduler 단위 테스트")
class OutboxPollingSchedulerTest {

    @InjectMocks
    private OutboxPollingScheduler scheduler;

    @Mock
    private OutboxPublisher outboxPublisher;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private OrderMetricsPort orderMetrics;

    @Captor
    private ArgumentCaptor<OutboxPublisher.EventSender> senderCaptor;

    @Test
    @DisplayName("pollAndPublish 호출 시 OutboxPublisher에 이벤트 전송을 위임한다")
    void pollAndPublish_delegatesToOutboxPublisher() {
        scheduler.pollAndPublish();

        verify(outboxPublisher).publishPendingEvents(any());
    }

    @Test
    @DisplayName("OrderPlaced 이벤트 전송 시 order.order.placed 토픽으로 Kafka 전송한다")
    void sendToKafka_orderPlaced_sendsToCorrectTopic() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        scheduler.pollAndPublish();
        verify(outboxPublisher).publishPendingEvents(senderCaptor.capture());

        OutboxPublisher.EventSender sender = senderCaptor.getValue();
        boolean result = sender.send("OrderPlaced", "order-1", "{\"test\":true}");

        assertThat(result).isTrue();
        verify(kafkaTemplate).send("order.order.placed", "order-1", "{\"test\":true}");
    }

    @Test
    @DisplayName("OrderCancelled 이벤트 전송 시 order.order.cancelled 토픽으로 Kafka 전송한다")
    void sendToKafka_orderCancelled_sendsToCorrectTopic() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        scheduler.pollAndPublish();
        verify(outboxPublisher).publishPendingEvents(senderCaptor.capture());

        OutboxPublisher.EventSender sender = senderCaptor.getValue();
        boolean result = sender.send("OrderCancelled", "order-1", "{\"test\":true}");

        assertThat(result).isTrue();
        verify(kafkaTemplate).send("order.order.cancelled", "order-1", "{\"test\":true}");
    }

    @Test
    @DisplayName("Kafka 전송 실패 시 false를 반환하고 메트릭을 기록한다")
    void sendToKafka_failure_returnsFalseAndRecordsMetric() {
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Kafka unavailable")));

        scheduler.pollAndPublish();
        verify(outboxPublisher).publishPendingEvents(senderCaptor.capture());

        OutboxPublisher.EventSender sender = senderCaptor.getValue();
        boolean result = sender.send("OrderPlaced", "order-1", "{\"test\":true}");

        assertThat(result).isFalse();
        verify(orderMetrics).recordEventPublishFailure("OrderPlaced");
    }
}
