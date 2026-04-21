package com.example.notification.adapter.in.rest.dto.response;

import com.example.notification.application.result.GetNotificationResult;

public record NotificationDetailResponse(
        String notificationId,
        String channel,
        String subject,
        String body,
        String status,
        String sentAt,
        String createdAt
) {
    public static NotificationDetailResponse from(GetNotificationResult result) {
        return new NotificationDetailResponse(
                result.notificationId(),
                result.channel(),
                result.subject(),
                result.body(),
                result.status(),
                result.sentAt() != null ? result.sentAt().toString() : null,
                result.createdAt().toString()
        );
    }
}
