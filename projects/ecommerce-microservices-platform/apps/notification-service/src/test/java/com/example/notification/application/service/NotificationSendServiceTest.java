package com.example.notification.application.service;

import com.example.notification.application.command.SendNotificationCommand;
import com.example.notification.application.port.in.ManagePreferenceUseCase;
import com.example.notification.application.port.out.NotificationRepository;
import com.example.notification.application.port.out.NotificationSender;
import com.example.notification.application.port.out.TemplateRepository;
import com.example.notification.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationSendService 단위 테스트")
class NotificationSendServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private TemplateRepository templateRepository;

    @Mock
    private ManagePreferenceUseCase managePreferenceUseCase;

    @Mock
    private NotificationSender emailSender;

    @InjectMocks
    private NotificationSendService notificationSendService;

    @Test
    @DisplayName("중복 이벤트 수신 시 알림을 발송하지 않는다")
    void sendNotification_duplicateEvent_skips() {
        given(notificationRepository.existsByEventId("event-1")).willReturn(true);

        SendNotificationCommand command = new SendNotificationCommand(
                "user-1", "event-1", TemplateType.ORDER_PLACED, Map.of());

        notificationSendService.sendNotification(command);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("사용자 설정이 없으면 기본 설정을 생성하고 알림을 발송한다")
    void sendNotification_noPreference_createsDefault() {
        given(notificationRepository.existsByEventId("event-1")).willReturn(false);

        UserNotificationPreference defaultPref = UserNotificationPreference.createDefault("user-1");
        given(managePreferenceUseCase.getOrCreatePreference("user-1")).willReturn(defaultPref);

        NotificationTemplate emailTemplate = NotificationTemplate.create(
                TemplateType.ORDER_PLACED, NotificationChannel.EMAIL,
                "Order {{orderId}}", "Your order {{orderId}} placed.");
        given(templateRepository.findByTypeAndChannel(TemplateType.ORDER_PLACED, NotificationChannel.EMAIL))
                .willReturn(Optional.of(emailTemplate));

        given(emailSender.supportedChannel()).willReturn(NotificationChannel.EMAIL);
        given(notificationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // Inject the sender list via reflection since @InjectMocks won't do it for List<>
        notificationSendService = new NotificationSendService(
                notificationRepository, templateRepository, managePreferenceUseCase, List.of(emailSender));

        SendNotificationCommand command = new SendNotificationCommand(
                "user-1", "event-1", TemplateType.ORDER_PLACED, Map.of("orderId", "ORD-1"));

        notificationSendService.sendNotification(command);

        verify(emailSender).send(anyString(), anyString(), anyString());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("비활성화된 채널로는 알림을 발송하지 않는다")
    void sendNotification_disabledChannel_skips() {
        given(notificationRepository.existsByEventId("event-1")).willReturn(false);

        UserNotificationPreference pref = UserNotificationPreference.createDefault("user-1");
        pref.update(false, false, false); // all disabled
        given(managePreferenceUseCase.getOrCreatePreference("user-1")).willReturn(pref);

        given(emailSender.supportedChannel()).willReturn(NotificationChannel.EMAIL);

        notificationSendService = new NotificationSendService(
                notificationRepository, templateRepository, managePreferenceUseCase, List.of(emailSender));

        SendNotificationCommand command = new SendNotificationCommand(
                "user-1", "event-1", TemplateType.ORDER_PLACED, Map.of());

        notificationSendService.sendNotification(command);

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("발송 실패 시 FAILED 상태로 저장된다")
    void sendNotification_sendFails_markedFailed() {
        given(notificationRepository.existsByEventId("event-1")).willReturn(false);

        UserNotificationPreference pref = UserNotificationPreference.createDefault("user-1");
        given(managePreferenceUseCase.getOrCreatePreference("user-1")).willReturn(pref);

        NotificationTemplate emailTemplate = NotificationTemplate.create(
                TemplateType.ORDER_PLACED, NotificationChannel.EMAIL,
                "Subject", "Body");
        given(templateRepository.findByTypeAndChannel(TemplateType.ORDER_PLACED, NotificationChannel.EMAIL))
                .willReturn(Optional.of(emailTemplate));

        given(emailSender.supportedChannel()).willReturn(NotificationChannel.EMAIL);
        doThrow(new RuntimeException("SMTP error")).when(emailSender).send(anyString(), anyString(), anyString());
        given(notificationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        notificationSendService = new NotificationSendService(
                notificationRepository, templateRepository, managePreferenceUseCase, List.of(emailSender));

        SendNotificationCommand command = new SendNotificationCommand(
                "user-1", "event-1", TemplateType.ORDER_PLACED, Map.of());

        notificationSendService.sendNotification(command);

        verify(notificationRepository).save(argThat(n ->
                n.getStatus() == NotificationStatus.FAILED && n.getRetryCount() == 1));
    }
}
