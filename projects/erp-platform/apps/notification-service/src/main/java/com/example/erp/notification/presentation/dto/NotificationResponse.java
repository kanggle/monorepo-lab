package com.example.erp.notification.presentation.dto;

import com.example.erp.notification.domain.notification.Notification;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/**
 * Inbox notification DTO (notification-api.md § Common shape). Follows the
 * {@code @JsonInclude(NON_NULL)} absent-field convention — {@code readAt} is
 * <b>omitted</b> while {@code read == false}, never serialized as {@code null}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NotificationResponse(
        String id,
        String type,
        String title,
        String body,
        String sourceType,
        String sourceId,
        boolean read,
        Instant createdAt,
        Instant readAt) {

    public static NotificationResponse from(Notification n) {
        return new NotificationResponse(
                n.id(),
                n.type().name(),
                n.title(),
                n.body(),
                n.source().sourceType().name(),
                n.source().sourceId(),
                n.read(),
                n.createdAt(),
                n.readAt().orElse(null));
    }
}
