package com.example.notification.adapter.out.persistence.mapper;

import com.example.notification.adapter.out.persistence.entity.NotificationJpaEntity;
import com.example.notification.domain.model.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationPersistenceMapper {

    public Notification toDomain(NotificationJpaEntity entity) {
        return Notification.reconstitute(
                entity.getNotificationId(),
                entity.getUserId(),
                entity.getChannel(),
                entity.getSubject(),
                entity.getBody(),
                entity.getStatus(),
                entity.getEventId(),
                entity.getRetryCount(),
                entity.getSentAt(),
                entity.getCreatedAt()
        );
    }

    public NotificationJpaEntity toEntity(Notification notification) {
        NotificationJpaEntity entity = new NotificationJpaEntity();
        entity.setNotificationId(notification.getNotificationId());
        entity.setUserId(notification.getUserId());
        entity.setChannel(notification.getChannel());
        entity.setSubject(notification.getSubject());
        entity.setBody(notification.getBody());
        entity.setStatus(notification.getStatus());
        entity.setEventId(notification.getEventId());
        entity.setRetryCount(notification.getRetryCount());
        entity.setSentAt(notification.getSentAt());
        entity.setCreatedAt(notification.getCreatedAt());
        return entity;
    }
}
