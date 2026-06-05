package com.example.erp.notification.application;

import com.example.erp.notification.application.command.NotifyOnApprovalCommand;
import com.example.erp.notification.application.port.outbound.ClockPort;
import com.example.erp.notification.application.port.outbound.IdGeneratorPort;
import com.example.erp.notification.application.port.outbound.NotificationChannelPort;
import com.example.erp.notification.application.port.outbound.NotificationMetricsPort;
import com.example.erp.notification.domain.delivery.DeliveryChannel;
import com.example.erp.notification.domain.delivery.DeliveryStatus;
import com.example.erp.notification.domain.delivery.NotificationDelivery;
import com.example.erp.notification.domain.delivery.repository.NotificationDeliveryRepository;
import com.example.erp.notification.domain.notification.Notification;
import com.example.erp.notification.domain.notification.NotificationType;
import com.example.erp.notification.domain.notification.repository.NotificationRepository;
import com.example.erp.notification.domain.recipient.RecipientResolver;
import com.example.erp.notification.domain.render.ApprovalEvent;
import com.example.erp.notification.domain.render.NotificationFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class NotifyOnApprovalEventUseCaseTest {

    @Mock NotificationRepository notificationRepository;
    @Mock NotificationDeliveryRepository deliveryRepository;
    @Mock EventDedupeService dedupeService;
    @Mock IdGeneratorPort idGenerator;
    @Mock ClockPort clock;
    @Mock NotificationMetricsPort metrics;
    @Mock NotificationChannelPort inAppChannel;

    private NotifyOnApprovalEventUseCase useCase;

    private final Instant now = Instant.parse("2026-06-05T10:00:00Z");

    @BeforeEach
    void setUp() {
        useCase = new NotifyOnApprovalEventUseCase(
                new RecipientResolver(), new NotificationFactory(),
                notificationRepository, deliveryRepository, dedupeService,
                idGenerator, clock, metrics, List.of(inAppChannel));
    }

    private NotifyOnApprovalCommand command(NotificationType type) {
        ApprovalEvent event = new ApprovalEvent("evt-1", type, "erp", "appr-1",
                "DEPARTMENT", "dept-1", "emp-approver", "emp-submitter",
                "2026-06-05T11:00:00Z", type == NotificationType.APPROVAL_REJECTED ? "사유" : null);
        return new NotifyOnApprovalCommand(event, "erp.approval." + type.name().toLowerCase());
    }

    private void stubHappyPath() {
        when(dedupeService.isDuplicate("evt-1")).thenReturn(false);
        when(clock.now()).thenReturn(now);
        when(idGenerator.newNotificationId()).thenReturn("ntf-1");
        when(idGenerator.newDeliveryId()).thenReturn("dlv-1");
        when(inAppChannel.channel()).thenReturn(DeliveryChannel.IN_APP);
        when(inAppChannel.deliver(any())).thenReturn(NotificationChannelPort.DeliveryOutcome.ofDelivered());
    }

    @Test
    void submittedNotifiesApproverAndDeliversInApp() {
        stubHappyPath();
        useCase.handle(command(NotificationType.APPROVAL_SUBMITTED));

        ArgumentCaptor<Notification> notif = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notif.capture());
        assertThat(notif.getValue().recipientId()).isEqualTo("emp-approver");
        assertThat(notif.getValue().type()).isEqualTo(NotificationType.APPROVAL_SUBMITTED);

        ArgumentCaptor<NotificationDelivery> delivery = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository).save(delivery.capture());
        assertThat(delivery.getValue().status()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.getValue().attemptCount()).isEqualTo(1);
        assertThat(delivery.getValue().channel()).isEqualTo(DeliveryChannel.IN_APP);

        verify(dedupeService).markProcessed("evt-1",
                "erp.approval.approval_submitted", "appr-1");
        verify(metrics).dispatched(NotificationType.APPROVAL_SUBMITTED);
        verify(metrics).deliveryStatus(DeliveryStatus.DELIVERED);
    }

    @Test
    void approvedNotifiesSubmitter() {
        stubHappyPath();
        useCase.handle(command(NotificationType.APPROVAL_APPROVED));
        ArgumentCaptor<Notification> notif = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notif.capture());
        assertThat(notif.getValue().recipientId()).isEqualTo("emp-submitter");
    }

    @Test
    void withdrawnNotifiesApprover() {
        stubHappyPath();
        useCase.handle(command(NotificationType.APPROVAL_WITHDRAWN));
        ArgumentCaptor<Notification> notif = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notif.capture());
        assertThat(notif.getValue().recipientId()).isEqualTo("emp-approver");
    }

    @Test
    void duplicateEventIdIsSkippedWithoutMutation() {
        when(dedupeService.isDuplicate("evt-1")).thenReturn(true);
        useCase.handle(command(NotificationType.APPROVAL_SUBMITTED));

        verify(notificationRepository, never()).save(any());
        verify(deliveryRepository, never()).save(any());
        verify(dedupeService, never()).markProcessed(any(), any(), any());
        verify(metrics).dedupeSkipped();
    }
}
