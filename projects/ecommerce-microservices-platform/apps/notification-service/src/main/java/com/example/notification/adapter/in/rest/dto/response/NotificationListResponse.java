package com.example.notification.adapter.in.rest.dto.response;

import com.example.common.page.PageResult;
import com.example.notification.application.result.NotificationSummary;

import java.util.List;

public record NotificationListResponse(
        List<NotificationSummaryItem> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
    public record NotificationSummaryItem(
            String notificationId,
            String channel,
            String subject,
            String status,
            String sentAt,
            String createdAt
    ) {
        public static NotificationSummaryItem from(NotificationSummary summary) {
            return new NotificationSummaryItem(
                    summary.notificationId(),
                    summary.channel(),
                    summary.subject(),
                    summary.status(),
                    summary.sentAt() != null ? summary.sentAt().toString() : null,
                    summary.createdAt().toString()
            );
        }
    }

    public static NotificationListResponse from(PageResult<NotificationSummary> pageResult) {
        return new NotificationListResponse(
                pageResult.content().stream().map(NotificationSummaryItem::from).toList(),
                pageResult.page(),
                pageResult.size(),
                pageResult.totalElements(),
                pageResult.totalPages()
        );
    }
}
