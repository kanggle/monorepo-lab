package com.example.notification.application.port.in;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.notification.application.result.GetNotificationResult;
import com.example.notification.application.result.NotificationSummary;

public interface QueryNotificationUseCase {
    PageResult<NotificationSummary> getNotifications(String userId, PageQuery pageQuery);
    GetNotificationResult getNotificationDetail(String userId, String notificationId);
}
