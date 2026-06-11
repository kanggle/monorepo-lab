package com.example.fanplatform.notification.presentation.dto;

import com.example.fanplatform.notification.domain.notification.Notification;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Inbox notification DTO. Follows the {@code @JsonInclude(NON_NULL)} absent-field
 * convention (§14) — {@code readAt} is <b>omitted</b> while the notification is
 * UNREAD, never serialized as {@code null}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationResponse(
        String id,
        String type,
        String title,
        String body,
        String status,
        boolean read,
        String membershipId,
        Instant createdAt,
        Instant readAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.getId(),
                n.getType().name(),
                n.getTitle(),
                n.getBody(),
                n.getStatus().name(),
                n.isRead(),
                n.getMembershipId(),
                n.getCreatedAt(),
                n.getReadAt());
    }
}
