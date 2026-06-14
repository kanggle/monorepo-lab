package com.example.notification.adapter.in.event;

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
@DisplayName("ShippingStatusChangedEventConsumer 단위 테스트")
class ShippingStatusChangedEventConsumerTest {

    @InjectMocks
    private ShippingStatusChangedEventConsumer consumer;

    @Mock
    private SendNotificationUseCase notificationSendService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("유효한 ShippingStatusChanged 이벤트를 처리하면 알림을 발송한다")
    void handle_validEvent_sendsNotification() {
        ShippingStatusChangedEvent event = new ShippingStatusChangedEvent(
                "event-1", "ShippingStatusChanged", "2026-04-06T00:00:00Z", "shipping-service", "tenant-x",
                new ShippingStatusChangedEvent.ShippingStatusChangedPayload(
                        "shipping-1", "order-1", "user-1",
                        "IN_TRANSIT", "DELIVERED", "TRACK-123", "CJ", "2026-04-06T10:00:00Z"));

        consumer.handle(event);

        verify(notificationSendService).sendNotification(argThat(cmd ->
                cmd.tenantId().equals("tenant-x") &&
                cmd.userId().equals("user-1") &&
                cmd.eventId().equals("event-1") &&
                cmd.templateType() == TemplateType.SHIPPING_STATUS_CHANGED &&
                cmd.variables().get("orderId").equals("order-1") &&
                cmd.variables().get("newStatus").equals("DELIVERED") &&
                cmd.variables().get("trackingNumber").equals("TRACK-123") &&
                cmd.variables().get("carrier").equals("CJ")));
    }

    @Test
    @DisplayName("payload가 null이면 알림을 발송하지 않는다")
    void handle_nullPayload_skips() {
        ShippingStatusChangedEvent event = new ShippingStatusChangedEvent(
                "event-1", "ShippingStatusChanged", "2026-04-06T00:00:00Z", "shipping-service", null, null);

        consumer.handle(event);

        verify(notificationSendService, never()).sendNotification(argThat(cmd -> true));
    }

    @Test
    @DisplayName("userId가 null이면 알림을 발송하지 않는다")
    void handle_nullUserId_skips() {
        ShippingStatusChangedEvent event = new ShippingStatusChangedEvent(
                "event-1", "ShippingStatusChanged", "2026-04-06T00:00:00Z", "shipping-service", null,
                new ShippingStatusChangedEvent.ShippingStatusChangedPayload(
                        "shipping-1", "order-1", null,
                        "IN_TRANSIT", "DELIVERED", "TRACK-123", "CJ", "2026-04-06T10:00:00Z"));

        consumer.handle(event);

        verify(notificationSendService, never()).sendNotification(argThat(cmd -> true));
    }

    @Test
    @DisplayName("payload 필드가 null이면 빈 문자열로 변환하여 알림을 발송한다")
    void handle_nullPayloadFields_sendsWithEmptyStrings() {
        ShippingStatusChangedEvent event = new ShippingStatusChangedEvent(
                "event-2", "ShippingStatusChanged", "2026-04-06T00:00:00Z", "shipping-service", null,
                new ShippingStatusChangedEvent.ShippingStatusChangedPayload(
                        "shipping-1", null, "user-1",
                        null, null, null, null, null));

        consumer.handle(event);

        verify(notificationSendService).sendNotification(argThat(cmd ->
                cmd.userId().equals("user-1") &&
                cmd.variables().get("orderId").equals("") &&
                cmd.variables().get("newStatus").equals("") &&
                cmd.variables().get("trackingNumber").equals("") &&
                cmd.variables().get("carrier").equals("")));
    }

    @Test
    @DisplayName("JSON 메시지를 역직렬화하고 처리한다")
    void onMessage_validJson_processes() throws Exception {
        String json = """
                {
                    "event_id": "evt-1",
                    "event_type": "ShippingStatusChanged",
                    "occurred_at": "2026-04-06T00:00:00Z",
                    "source": "shipping-service",
                    "payload": {
                        "shippingId": "ship-1",
                        "orderId": "order-1",
                        "userId": "user-1",
                        "previousStatus": "IN_TRANSIT",
                        "newStatus": "DELIVERED",
                        "trackingNumber": "TRACK-999",
                        "carrier": "HANJIN",
                        "changedAt": "2026-04-06T15:00:00Z"
                    }
                }
                """;

        consumer.onMessage(json);

        verify(notificationSendService).sendNotification(argThat(cmd ->
                cmd.userId().equals("user-1") && cmd.eventId().equals("evt-1")));
    }
}
