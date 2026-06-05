package com.example.erp.notification.application.query;

import com.example.erp.notification.domain.notification.Notification;

import java.util.List;

/**
 * Result of a paginated inbox query: the recipient's notifications for the
 * requested page + the total count (for {@code meta.totalElements}).
 */
public record InboxPage(List<Notification> content, int page, int size, long totalElements) {
}
