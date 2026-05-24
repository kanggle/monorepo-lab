package com.example.notification.adapter.in.rest.dto.response;

import com.example.common.page.PageResult;
import com.example.notification.application.result.ListNotificationsResult;

import java.util.List;

public record NotificationListResponse(
        List<NotificationSummary> content,
        int page,
        int size,
        long totalElements
) {
    public record NotificationSummary(
            String notificationId,
            String channel,
            String subject,
            String status,
            String sentAt,
            String createdAt
    ) {
        public static NotificationSummary from(ListNotificationsResult.NotificationSummary summary) {
            return new NotificationSummary(
                    summary.notificationId(),
                    summary.channel(),
                    summary.subject(),
                    summary.status(),
                    summary.sentAt() != null ? summary.sentAt().toString() : null,
                    summary.createdAt().toString()
            );
        }
    }

    public static NotificationListResponse from(PageResult<ListNotificationsResult.NotificationSummary> pageResult) {
        return new NotificationListResponse(
                pageResult.content().stream().map(NotificationSummary::from).toList(),
                pageResult.page(),
                pageResult.size(),
                pageResult.totalElements()
        );
    }
}
