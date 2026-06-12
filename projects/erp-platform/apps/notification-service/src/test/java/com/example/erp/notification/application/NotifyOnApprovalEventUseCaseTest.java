package com.example.erp.notification.application;

import com.example.erp.notification.application.command.NotifyOnApprovalCommand;
import com.example.erp.notification.application.command.NotifyOnDelegationCommand;
import com.example.erp.notification.application.command.NotifyOnDelegationRevokedCommand;
import com.example.erp.notification.application.port.outbound.ClockPort;
import com.example.erp.notification.application.port.outbound.IdGeneratorPort;
import com.example.erp.notification.application.port.outbound.NotificationChannelPort;
import com.example.erp.notification.application.port.outbound.NotificationMetricsPort;
import com.example.erp.notification.config.ExternalNotificationProperties;
import com.example.erp.notification.domain.delivery.DeliveryChannel;
import com.example.erp.notification.domain.delivery.DeliveryStatus;
import com.example.erp.notification.domain.delivery.NotificationDelivery;
import com.example.erp.notification.domain.delivery.repository.NotificationDeliveryRepository;
import com.example.erp.notification.domain.notification.Notification;
import com.example.erp.notification.domain.notification.NotificationType;
import com.example.erp.notification.domain.notification.repository.NotificationRepository;
import com.example.erp.notification.domain.recipient.RecipientResolver;
import com.example.erp.notification.domain.render.ApprovalEvent;
import com.example.erp.notification.domain.render.DelegationEvent;
import com.example.erp.notification.domain.render.DelegationRevokedEvent;
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
import static org.mockito.Mockito.times;
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
    private ExternalNotificationProperties externalProperties;

    private final Instant now = Instant.parse("2026-06-05T10:00:00Z");

    @BeforeEach
    void setUp() {
        externalProperties = new ExternalNotificationProperties(); // enabled=false (net-zero)
        useCase = new NotifyOnApprovalEventUseCase(
                new RecipientResolver(), new NotificationFactory(),
                notificationRepository, deliveryRepository, dedupeService,
                idGenerator, clock, metrics, List.of(inAppChannel), externalProperties);
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
    void externalDisabledByDefaultCreatesOnlyInAppDelivery() {
        stubHappyPath();
        useCase.handle(command(NotificationType.APPROVAL_SUBMITTED));
        // net-zero: exactly one delivery (IN_APP), no external row.
        verify(deliveryRepository, times(1)).save(any());
    }

    @Test
    void externalEnabledAlsoCreatesPendingDueExternalDelivery() {
        externalProperties.setEnabled(true);
        when(dedupeService.isDuplicate("evt-1")).thenReturn(false);
        when(clock.now()).thenReturn(now);
        when(idGenerator.newNotificationId()).thenReturn("ntf-1");
        when(idGenerator.newDeliveryId()).thenReturn("dlv-inapp", "dlv-ext");
        when(inAppChannel.channel()).thenReturn(DeliveryChannel.IN_APP);
        when(inAppChannel.deliver(any())).thenReturn(NotificationChannelPort.DeliveryOutcome.ofDelivered());

        useCase.handle(command(NotificationType.APPROVAL_SUBMITTED));

        ArgumentCaptor<NotificationDelivery> deliveries = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository, times(2)).save(deliveries.capture());
        List<NotificationDelivery> saved = deliveries.getAllValues();
        // IN_APP delivered synchronously …
        assertThat(saved).anySatisfy(d -> {
            assertThat(d.channel()).isEqualTo(DeliveryChannel.IN_APP);
            assertThat(d.status()).isEqualTo(DeliveryStatus.DELIVERED);
        });
        // … plus a PENDING external delivery, immediately due (scheduledRetryAt set).
        assertThat(saved).anySatisfy(d -> {
            assertThat(d.channel()).isEqualTo(DeliveryChannel.SLACK);
            assertThat(d.status()).isEqualTo(DeliveryStatus.PENDING);
            assertThat(d.attemptCount()).isZero();
            assertThat(d.scheduledRetryAt()).contains(now);
        });
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

    // ---- TASK-ERP-BE-014: delegation handler ----

    private NotifyOnDelegationCommand delegationCommand() {
        DelegationEvent event = new DelegationEvent("evt-d", "erp", "dgr-1", "emp-A", "emp-D",
                "2026-06-06T00:00:00Z", null, null);
        return new NotifyOnDelegationCommand(event, "erp.approval.delegated.v1");
    }

    @Test
    void delegatedNotifiesDelegateAndDeliversInApp() {
        when(dedupeService.isDuplicate("evt-d")).thenReturn(false);
        when(clock.now()).thenReturn(now);
        when(idGenerator.newNotificationId()).thenReturn("ntf-d");
        when(idGenerator.newDeliveryId()).thenReturn("dlv-d");
        when(inAppChannel.channel()).thenReturn(DeliveryChannel.IN_APP);
        when(inAppChannel.deliver(any())).thenReturn(NotificationChannelPort.DeliveryOutcome.ofDelivered());

        useCase.handle(delegationCommand());

        ArgumentCaptor<Notification> notif = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notif.capture());
        assertThat(notif.getValue().recipientId()).isEqualTo("emp-D");
        assertThat(notif.getValue().type()).isEqualTo(NotificationType.DELEGATION_GRANTED);
        assertThat(notif.getValue().source().sourceId()).isEqualTo("dgr-1");

        ArgumentCaptor<NotificationDelivery> delivery = ArgumentCaptor.forClass(NotificationDelivery.class);
        verify(deliveryRepository).save(delivery.capture());
        assertThat(delivery.getValue().status()).isEqualTo(DeliveryStatus.DELIVERED);
        assertThat(delivery.getValue().attemptCount()).isEqualTo(1);

        // dedupe provenance keyed on grantId.
        verify(dedupeService).markProcessed("evt-d", "erp.approval.delegated.v1", "dgr-1");
        verify(metrics).dispatched(NotificationType.DELEGATION_GRANTED);
        verify(metrics).deliveryStatus(DeliveryStatus.DELIVERED);
    }

    @Test
    void duplicateDelegationEventIsSkippedWithoutMutation() {
        when(dedupeService.isDuplicate("evt-d")).thenReturn(true);
        useCase.handle(delegationCommand());

        verify(notificationRepository, never()).save(any());
        verify(deliveryRepository, never()).save(any());
        verify(dedupeService, never()).markProcessed(any(), any(), any());
        verify(metrics).dedupeSkipped();
    }

    // ---- TASK-ERP-BE-016: delegation-revoked handler ----

    private NotifyOnDelegationRevokedCommand revokedCommand() {
        DelegationRevokedEvent event = new DelegationRevokedEvent("evt-r", "erp", "dgr-1",
                "emp-A", "emp-D", "휴가 복귀");
        return new NotifyOnDelegationRevokedCommand(event, "erp.approval.delegation.revoked.v1");
    }

    @Test
    void revokedNotifiesDelegateAndDeliversInApp() {
        when(dedupeService.isDuplicate("evt-r")).thenReturn(false);
        when(clock.now()).thenReturn(now);
        when(idGenerator.newNotificationId()).thenReturn("ntf-r");
        when(idGenerator.newDeliveryId()).thenReturn("dlv-r");
        when(inAppChannel.channel()).thenReturn(DeliveryChannel.IN_APP);
        when(inAppChannel.deliver(any())).thenReturn(NotificationChannelPort.DeliveryOutcome.ofDelivered());

        useCase.handle(revokedCommand());

        ArgumentCaptor<Notification> notif = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(notif.capture());
        assertThat(notif.getValue().recipientId()).isEqualTo("emp-D");
        assertThat(notif.getValue().type()).isEqualTo(NotificationType.DELEGATION_REVOKED);
        assertThat(notif.getValue().source().sourceId()).isEqualTo("dgr-1");

        verify(dedupeService).markProcessed("evt-r", "erp.approval.delegation.revoked.v1", "dgr-1");
        verify(metrics).dispatched(NotificationType.DELEGATION_REVOKED);
        verify(metrics).deliveryStatus(DeliveryStatus.DELIVERED);
    }

    @Test
    void duplicateRevokedEventIsSkippedWithoutMutation() {
        when(dedupeService.isDuplicate("evt-r")).thenReturn(true);
        useCase.handle(revokedCommand());

        verify(notificationRepository, never()).save(any());
        verify(deliveryRepository, never()).save(any());
        verify(dedupeService, never()).markProcessed(any(), any(), any());
        verify(metrics).dedupeSkipped();
    }
}
