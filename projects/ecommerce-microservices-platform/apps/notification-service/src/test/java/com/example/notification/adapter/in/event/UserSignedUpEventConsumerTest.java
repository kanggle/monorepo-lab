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
@DisplayName("UserSignedUpEventConsumer 단위 테스트")
class UserSignedUpEventConsumerTest {

    @InjectMocks
    private UserSignedUpEventConsumer consumer;

    @Mock
    private SendNotificationUseCase notificationSendService;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("유효한 UserSignedUp 이벤트를 처리하면 WELCOME 알림을 발송한다")
    void handle_validEvent_sendsNotification() {
        UserSignedUpEvent event = new UserSignedUpEvent(
                "event-1", "UserSignedUp", "2026-03-28T00:00:00Z", "auth-service",
                new UserSignedUpEvent.UserSignedUpPayload("user-1", "test@example.com", "John"));

        consumer.handle(event);

        verify(notificationSendService).sendNotification(argThat(cmd ->
                cmd.userId().equals("user-1") &&
                cmd.templateType() == TemplateType.WELCOME));
    }

    @Test
    @DisplayName("payload가 null이면 알림을 발송하지 않는다")
    void handle_nullPayload_skips() {
        UserSignedUpEvent event = new UserSignedUpEvent(
                "event-1", "UserSignedUp", "2026-03-28T00:00:00Z", "auth-service", null);

        consumer.handle(event);

        verify(notificationSendService, never()).sendNotification(argThat(cmd -> true));
    }
}
