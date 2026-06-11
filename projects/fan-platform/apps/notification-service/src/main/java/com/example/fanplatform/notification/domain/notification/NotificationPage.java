package com.example.fanplatform.notification.domain.notification;

import java.util.List;

/**
 * Result of a paginated inbox query: the requested page of notifications + the
 * total count (for the {@code meta.totalElements} envelope field).
 */
public record NotificationPage(List<Notification> content, int page, int size, long totalElements) {
}
