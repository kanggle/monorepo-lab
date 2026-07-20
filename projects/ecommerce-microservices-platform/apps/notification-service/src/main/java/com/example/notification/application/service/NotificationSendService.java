package com.example.notification.application.service;

import com.example.notification.application.command.SendNotificationCommand;
import com.example.notification.application.port.in.ManagePreferenceUseCase;
import com.example.notification.application.port.in.SendNotificationUseCase;
import com.example.notification.application.port.out.NotificationMetricsPort;
import com.example.notification.application.port.out.NotificationRepository;
import com.example.notification.application.port.out.NotificationSender;
import com.example.notification.application.port.out.TemplateRepository;
import com.example.notification.domain.model.Notification;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.NotificationFailureReason;
import com.example.notification.domain.model.NotificationTemplate;
import com.example.notification.domain.model.UserNotificationPreference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class NotificationSendService implements SendNotificationUseCase {

    private final NotificationRepository notificationRepository;
    private final TemplateRepository templateRepository;
    private final ManagePreferenceUseCase managePreferenceUseCase;
    private final Map<NotificationChannel, NotificationSender> senderMap;
    private final NotificationMetricsPort metrics;

    public NotificationSendService(NotificationRepository notificationRepository,
                                   TemplateRepository templateRepository,
                                   ManagePreferenceUseCase managePreferenceUseCase,
                                   List<NotificationSender> notificationSenders,
                                   NotificationMetricsPort metrics) {
        this.notificationRepository = notificationRepository;
        this.templateRepository = templateRepository;
        this.managePreferenceUseCase = managePreferenceUseCase;
        this.metrics = metrics;
        List<NotificationSender> senders = notificationSenders != null ? notificationSenders : List.of();
        this.senderMap = senders.stream()
                .collect(Collectors.toMap(
                        NotificationSender::supportedChannel,
                        Function.identity(),
                        (existing, replacement) -> replacement));
    }

    @Transactional
    public void sendNotification(SendNotificationCommand command) {
        // The send path runs on a Kafka thread (no HTTP TenantContext); the originating
        // tenant is bound on the event envelope and threaded explicitly via the command so
        // dedup, preference resolution, template resolution and the created notification all
        // scope to it (TASK-BE-372 M4).
        if (notificationRepository.existsByEventId(command.eventId(), command.tenantId())) {
            log.info("Duplicate event detected, skipping. eventId={}", command.eventId());
            return;
        }

        UserNotificationPreference preference =
                managePreferenceUseCase.getOrCreatePreference(command.userId(), command.tenantId());

        for (NotificationChannel channel : NotificationChannel.values()) {
            sendViaChannel(command, channel, preference, senderMap);
        }
    }

    private void sendViaChannel(SendNotificationCommand command, NotificationChannel channel,
                                UserNotificationPreference preference,
                                Map<NotificationChannel, NotificationSender> senderMap) {
        if (!preference.isChannelEnabled(channel)) {
            log.debug("Channel {} is disabled for user {}", channel, command.userId());
            return;
        }

        NotificationSender sender = senderMap.get(channel);
        if (sender == null) {
            log.debug("No sender available for channel {}", channel);
            return;
        }

        templateRepository.findByTypeAndChannel(command.templateType(), channel, command.tenantId())
                .ifPresent(template -> renderAndSend(command, channel, template, sender));
    }

    private void renderAndSend(SendNotificationCommand command, NotificationChannel channel,
                               NotificationTemplate template, NotificationSender sender) {
        String renderedSubject = template.renderSubject(command.variables());
        String renderedBody = template.renderBody(command.variables());

        Notification notification = Notification.create(
                command.tenantId(), command.userId(), channel, renderedSubject, renderedBody, command.eventId());

        // This try/catch is the point where a real send failure surfaces for every channel:
        // EmailNotificationSender lets MailException propagate, and WebPushSender — which must
        // stay fail-soft per-subscription so one dead endpoint cannot abort the user's other
        // subscriptions — escalates by throwing when it delivered to none of them. Counting here
        // rather than inside each sender keeps sent/failed on one population (one notification
        // row), so the alert's failed/(failed+sent) ratio is a true rate (TASK-BE-533 / ADR-006).
        try {
            sender.send(command.userId(), renderedSubject, renderedBody);
            notification.markSent();
            metrics.recordSent(channel);
            log.info("Notification sent. userId={}, channel={}, eventId={}",
                    command.userId(), channel, command.eventId());
        } catch (Exception e) {
            notification.markFailed();
            metrics.recordFailed(channel, NotificationFailureReason.classify(e));
            log.error("Failed to send notification. userId={}, channel={}, eventId={}, error={}",
                    command.userId(), channel, command.eventId(), e.getMessage());
        }

        notificationRepository.save(notification);
    }

}
