package com.example.notification.application.service;

import com.example.notification.application.command.SendNotificationCommand;
import com.example.notification.application.port.in.ManagePreferenceUseCase;
import com.example.notification.application.port.out.NotificationRepository;
import com.example.notification.application.port.out.NotificationSender;
import com.example.notification.application.port.out.TemplateRepository;
import com.example.notification.adapter.out.metrics.MicrometerNotificationMetrics;
import com.example.notification.domain.model.*;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

    /**
     * A real Micrometer adapter over a real {@link SimpleMeterRegistry}, not a mock — TASK-BE-533
     * AC-1 asks for a test that drives the failure path and observes the counter. A mocked port
     * would prove only that a method was called, not that the series {@code alert-rules.yml}
     * queries actually comes into existence.
     */
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerNotificationMetrics metrics = new MicrometerNotificationMetrics(registry);

    private static final String TENANT = "ecommerce";

    @Test
    @DisplayName("중복 이벤트 수신 시 알림을 발송하지 않는다")
    void sendNotification_duplicateEvent_skips() {
        given(notificationRepository.existsByEventId("event-1", TENANT)).willReturn(true);

        SendNotificationCommand command = new SendNotificationCommand(
                TENANT, "user-1", "event-1", TemplateType.ORDER_PLACED, Map.of());

        notificationSendService.sendNotification(command);

        verify(notificationRepository, never()).save(any());
    }

    @Test
    @DisplayName("사용자 설정이 없으면 기본 설정을 생성하고 알림을 발송한다")
    void sendNotification_noPreference_createsDefault() {
        given(notificationRepository.existsByEventId("event-1", TENANT)).willReturn(false);

        UserNotificationPreference defaultPref = UserNotificationPreference.createDefault("user-1", TENANT);
        given(managePreferenceUseCase.getOrCreatePreference("user-1", TENANT)).willReturn(defaultPref);

        NotificationTemplate emailTemplate = NotificationTemplate.create(
                TemplateType.ORDER_PLACED, NotificationChannel.EMAIL,
                "Order {{orderId}}", "Your order {{orderId}} placed.");
        given(templateRepository.findByTypeAndChannel(TemplateType.ORDER_PLACED, NotificationChannel.EMAIL, TENANT))
                .willReturn(Optional.of(emailTemplate));

        given(emailSender.supportedChannel()).willReturn(NotificationChannel.EMAIL);
        given(notificationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        // Inject the sender list via reflection since @InjectMocks won't do it for List<>
        notificationSendService = new NotificationSendService(
                notificationRepository, templateRepository, managePreferenceUseCase, List.of(emailSender), metrics);

        SendNotificationCommand command = new SendNotificationCommand(
                TENANT, "user-1", "event-1", TemplateType.ORDER_PLACED, Map.of("orderId", "ORD-1"));

        notificationSendService.sendNotification(command);

        verify(emailSender).send(anyString(), anyString(), anyString());
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("비활성화된 채널로는 알림을 발송하지 않는다")
    void sendNotification_disabledChannel_skips() {
        given(notificationRepository.existsByEventId("event-1", TENANT)).willReturn(false);

        UserNotificationPreference pref = UserNotificationPreference.createDefault("user-1", TENANT);
        pref.update(false, false, false); // all disabled
        given(managePreferenceUseCase.getOrCreatePreference("user-1", TENANT)).willReturn(pref);

        given(emailSender.supportedChannel()).willReturn(NotificationChannel.EMAIL);

        notificationSendService = new NotificationSendService(
                notificationRepository, templateRepository, managePreferenceUseCase, List.of(emailSender), metrics);

        SendNotificationCommand command = new SendNotificationCommand(
                TENANT, "user-1", "event-1", TemplateType.ORDER_PLACED, Map.of());

        notificationSendService.sendNotification(command);

        verify(emailSender, never()).send(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("발송 실패 시 FAILED 상태로 저장된다")
    void sendNotification_sendFails_markedFailed() {
        given(notificationRepository.existsByEventId("event-1", TENANT)).willReturn(false);

        UserNotificationPreference pref = UserNotificationPreference.createDefault("user-1", TENANT);
        given(managePreferenceUseCase.getOrCreatePreference("user-1", TENANT)).willReturn(pref);

        NotificationTemplate emailTemplate = NotificationTemplate.create(
                TemplateType.ORDER_PLACED, NotificationChannel.EMAIL,
                "Subject", "Body");
        given(templateRepository.findByTypeAndChannel(TemplateType.ORDER_PLACED, NotificationChannel.EMAIL, TENANT))
                .willReturn(Optional.of(emailTemplate));

        given(emailSender.supportedChannel()).willReturn(NotificationChannel.EMAIL);
        doThrow(new RuntimeException("SMTP error")).when(emailSender).send(anyString(), anyString(), anyString());
        given(notificationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        notificationSendService = new NotificationSendService(
                notificationRepository, templateRepository, managePreferenceUseCase, List.of(emailSender), metrics);

        SendNotificationCommand command = new SendNotificationCommand(
                TENANT, "user-1", "event-1", TemplateType.ORDER_PLACED, Map.of());

        notificationSendService.sendNotification(command);

        verify(notificationRepository).save(argThat(n ->
                n.getStatus() == NotificationStatus.FAILED && n.getRetryCount() == 1));
    }

    /**
     * TASK-BE-533 AC-1 / F1 — the defect this guards against is a counter that is registered and
     * never incremented: the alert then has a series permanently at zero, which reads as "no
     * failures" rather than "not measured". So the assertion drives a real send failure through
     * the service and reads the counter back out of the registry.
     */
    @Test
    @DisplayName("BE-533: 발송 실패가 notification_failed_total{channel,reason} 을 실제로 증가시킨다")
    void sendNotification_sendFails_incrementsFailureCounter() {
        givenSendableEmailTemplate();
        doThrow(new org.springframework.mail.MailSendException("SMTP relay unreachable"))
                .when(emailSender).send(anyString(), anyString(), anyString());

        notificationSendService = new NotificationSendService(
                notificationRepository, templateRepository, managePreferenceUseCase, List.of(emailSender), metrics);

        notificationSendService.sendNotification(new SendNotificationCommand(
                TENANT, "user-1", "event-1", TemplateType.ORDER_PLACED, Map.of()));

        Counter failed = registry.find("notification_failed_total")
                .tag("channel", "email").tag("reason", "mail_send").counter();
        assertThat(failed).as("failure counter must exist after a failed send").isNotNull();
        assertThat(failed.count()).isEqualTo(1.0);
        assertThat(registry.find("notification_sent_total").counter())
                .as("a failed send must not also be counted as sent").isNull();
    }

    @Test
    @DisplayName("BE-533: 발송 성공이 notification_sent_total{channel} 을 증가시킨다")
    void sendNotification_sendSucceeds_incrementsSentCounter() {
        givenSendableEmailTemplate();

        notificationSendService = new NotificationSendService(
                notificationRepository, templateRepository, managePreferenceUseCase, List.of(emailSender), metrics);

        notificationSendService.sendNotification(new SendNotificationCommand(
                TENANT, "user-1", "event-1", TemplateType.ORDER_PLACED, Map.of()));

        Counter sent = registry.find("notification_sent_total").tag("channel", "email").counter();
        assertThat(sent).isNotNull();
        assertThat(sent.count()).isEqualTo(1.0);
        assertThat(registry.find("notification_failed_total").counter()).isNull();
    }

    private void givenSendableEmailTemplate() {
        given(notificationRepository.existsByEventId("event-1", TENANT)).willReturn(false);
        given(managePreferenceUseCase.getOrCreatePreference("user-1", TENANT))
                .willReturn(UserNotificationPreference.createDefault("user-1", TENANT));
        given(templateRepository.findByTypeAndChannel(TemplateType.ORDER_PLACED, NotificationChannel.EMAIL, TENANT))
                .willReturn(Optional.of(NotificationTemplate.create(
                        TemplateType.ORDER_PLACED, NotificationChannel.EMAIL, "Subject", "Body")));
        given(emailSender.supportedChannel()).willReturn(NotificationChannel.EMAIL);
        given(notificationRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("senderMap은 생성자에서 한 번만 빌드되며 매 호출마다 동일 인스턴스를 반환한다")
    void senderMap_cachedInConstructor_sameInstanceOnEveryCall() throws Exception {
        given(emailSender.supportedChannel()).willReturn(NotificationChannel.EMAIL);
        NotificationSendService service = new NotificationSendService(
                notificationRepository, templateRepository, managePreferenceUseCase, List.of(emailSender), metrics);

        Field field = NotificationSendService.class.getDeclaredField("senderMap");
        field.setAccessible(true);
        Object firstRef = field.get(service);
        Object secondRef = field.get(service);

        assertThat(firstRef).isSameAs(secondRef);
    }
}
