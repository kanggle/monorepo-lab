package com.example.notification.application.service;

import com.example.notification.application.command.SendNotificationCommand;
import com.example.notification.application.port.in.ManagePreferenceUseCase;
import com.example.notification.application.port.in.SendNotificationUseCase;
import com.example.notification.application.port.out.NotificationRepository;
import com.example.notification.application.port.out.NotificationSender;
import com.example.notification.application.port.out.TemplateRepository;
import com.example.notification.domain.model.Notification;
import com.example.notification.domain.model.NotificationChannel;
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

    public NotificationSendService(NotificationRepository notificationRepository,
                                   TemplateRepository templateRepository,
                                   ManagePreferenceUseCase managePreferenceUseCase,
                                   List<NotificationSender> notificationSenders) {
        this.notificationRepository = notificationRepository;
        this.templateRepository = templateRepository;
        this.managePreferenceUseCase = managePreferenceUseCase;
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

        try {
            sender.send(command.userId(), renderedSubject, renderedBody);
            notification.markSent();
            log.info("Notification sent. userId={}, channel={}, eventId={}",
                    command.userId(), channel, command.eventId());
        } catch (Exception e) {
            notification.markFailed();
            log.error("Failed to send notification. userId={}, channel={}, eventId={}, error={}",
                    command.userId(), channel, command.eventId(), e.getMessage());
        }

        notificationRepository.save(notification);
    }

}
