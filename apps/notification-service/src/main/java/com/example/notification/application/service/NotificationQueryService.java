package com.example.notification.application.service;

import com.example.notification.application.page.PageQuery;
import com.example.notification.application.page.PageResult;
import com.example.notification.application.port.in.QueryNotificationUseCase;
import com.example.notification.application.port.out.NotificationRepository;
import com.example.notification.application.result.GetNotificationResult;
import com.example.notification.application.result.ListNotificationsResult;
import com.example.notification.domain.exception.NotificationNotFoundException;
import com.example.notification.domain.exception.UnauthorizedNotificationAccessException;
import com.example.notification.domain.model.Notification;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService implements QueryNotificationUseCase {

    private final NotificationRepository notificationRepository;

    public PageResult<ListNotificationsResult.NotificationSummary> getNotifications(String userId, PageQuery pageQuery) {
        PageResult<Notification> pageResult = notificationRepository.findByUserId(userId, pageQuery);
        return PageResult.of(
                pageResult.content().stream().map(ListNotificationsResult.NotificationSummary::from).toList(),
                pageResult.totalElements(),
                pageResult.totalPages(),
                pageResult.page(),
                pageResult.size()
        );
    }

    public GetNotificationResult getNotificationDetail(String userId, String notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        if (!notification.getUserId().equals(userId)) {
            throw new UnauthorizedNotificationAccessException(
                    "User does not have access to this notification");
        }

        return GetNotificationResult.from(notification);
    }
}
