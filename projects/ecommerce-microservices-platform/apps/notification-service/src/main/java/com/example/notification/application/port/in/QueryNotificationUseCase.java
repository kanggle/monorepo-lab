package com.example.notification.application.port.in;

import com.example.notification.application.page.PageQuery;
import com.example.notification.application.page.PageResult;
import com.example.notification.application.result.GetNotificationResult;
import com.example.notification.application.result.ListNotificationsResult;

public interface QueryNotificationUseCase {
    PageResult<ListNotificationsResult.NotificationSummary> getNotifications(String userId, PageQuery pageQuery);
    GetNotificationResult getNotificationDetail(String userId, String notificationId);
}
