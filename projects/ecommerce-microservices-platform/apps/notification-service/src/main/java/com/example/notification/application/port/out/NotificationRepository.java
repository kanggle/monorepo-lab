package com.example.notification.application.port.out;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.notification.domain.model.Notification;

import java.util.Optional;

public interface NotificationRepository {
    Notification save(Notification notification);
    Optional<Notification> findById(String notificationId);
    PageResult<Notification> findByUserId(String userId, PageQuery pageQuery);
    boolean existsByEventId(String eventId);
}
