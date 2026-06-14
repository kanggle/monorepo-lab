package com.example.notification.adapter.in.event;

import com.example.notification.application.command.SendNotificationCommand;
import com.example.notification.application.port.in.SendNotificationUseCase;
import com.example.notification.domain.model.TemplateType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPlacedEventConsumer 단위 테스트")
class OrderPlacedEventConsumerTest {

    @InjectMocks
    private OrderPlacedEventConsumer consumer;

    @Mock
    private SendNotificationUseCase notificationSendService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("유효한 OrderPlaced 이벤트를 처리하면 알림을 발송한다")
    void handle_validEvent_sendsNotification() {
        OrderPlacedEvent event = new OrderPlacedEvent(
                "event-1", "OrderPlaced", "2026-03-28T00:00:00Z", "order-service", "tenant-x",
                new OrderPlacedEvent.OrderPlacedPayload("order-1", "user-1", 50000L));

        consumer.handle(event);

        verify(notificationSendService).sendNotification(argThat(cmd ->
                cmd.tenantId().equals("tenant-x") &&
                cmd.userId().equals("user-1") &&
                cmd.eventId().equals("event-1") &&
                cmd.templateType() == TemplateType.ORDER_PLACED));
    }

    @Test
    @DisplayName("payload가 null이면 알림을 발송하지 않는다")
    void handle_nullPayload_skips() {
        OrderPlacedEvent event = new OrderPlacedEvent(
                "event-1", "OrderPlaced", "2026-03-28T00:00:00Z", "order-service", null, null);

        consumer.handle(event);

        verify(notificationSendService, never()).sendNotification(argThat(cmd -> true));
    }

    @Test
    @DisplayName("userId가 null이면 알림을 발송하지 않는다")
    void handle_nullUserId_skips() {
        OrderPlacedEvent event = new OrderPlacedEvent(
                "event-1", "OrderPlaced", "2026-03-28T00:00:00Z", "order-service", null,
                new OrderPlacedEvent.OrderPlacedPayload("order-1", null, 50000L));

        consumer.handle(event);

        verify(notificationSendService, never()).sendNotification(argThat(cmd -> true));
    }

    @Test
    @DisplayName("JSON 메시지를 역직렬화하고 처리한다")
    void onMessage_validJson_processes() throws Exception {
        String json = """
                {
                    "event_id": "evt-1",
                    "event_type": "OrderPlaced",
                    "occurred_at": "2026-03-28T00:00:00Z",
                    "source": "order-service",
                    "payload": {
                        "orderId": "order-1",
                        "userId": "user-1",
                        "totalPrice": 30000
                    }
                }
                """;

        consumer.onMessage(json);

        verify(notificationSendService).sendNotification(argThat(cmd ->
                cmd.userId().equals("user-1") && cmd.eventId().equals("evt-1")));
    }
}
