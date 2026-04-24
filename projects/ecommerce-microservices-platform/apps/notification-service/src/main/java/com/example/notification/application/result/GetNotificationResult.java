package com.example.notification.application.result;

import com.example.notification.domain.model.Notification;

import java.time.LocalDateTime;

public record GetNotificationResult(
        String notificationId,
        String userId,
        String channel,
        String subject,
        String body,
        String status,
        LocalDateTime sentAt,
        LocalDateTime createdAt
) {
    public static GetNotificationResult from(Notification notification) {
        return new GetNotificationResult(
                notification.getNotificationId(),
                notification.getUserId(),
                notification.getChannel().name(),
                notification.getSubject(),
                notification.getBody(),
                notification.getStatus().name(),
                notification.getSentAt(),
                notification.getCreatedAt()
        );
    }
}
