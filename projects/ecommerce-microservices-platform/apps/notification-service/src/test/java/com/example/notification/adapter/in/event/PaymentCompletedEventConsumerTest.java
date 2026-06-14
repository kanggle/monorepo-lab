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
@DisplayName("PaymentCompletedEventConsumer 단위 테스트")
class PaymentCompletedEventConsumerTest {

    @InjectMocks
    private PaymentCompletedEventConsumer consumer;

    @Mock
    private SendNotificationUseCase notificationSendService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("유효한 PaymentCompleted 이벤트를 처리하면 알림을 발송한다")
    void handle_validEvent_sendsNotification() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                "event-1", "PaymentCompleted", "2026-03-28T00:00:00Z", "payment-service", "tenant-x",
                new PaymentCompletedEvent.PaymentCompletedPayload(
                        "pay-1", "order-1", "user-1", 50000L, "2026-03-28T00:00:00Z"));

        consumer.handle(event);

        verify(notificationSendService).sendNotification(argThat(cmd ->
                cmd.tenantId().equals("tenant-x") &&
                cmd.userId().equals("user-1") &&
                cmd.templateType() == TemplateType.PAYMENT_COMPLETED));
    }

    @Test
    @DisplayName("payload가 null이면 알림을 발송하지 않는다")
    void handle_nullPayload_skips() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                "event-1", "PaymentCompleted", "2026-03-28T00:00:00Z", "payment-service", null, null);

        consumer.handle(event);

        verify(notificationSendService, never()).sendNotification(argThat(cmd -> true));
    }
}
