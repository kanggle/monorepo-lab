package com.example.erp.notification.infrastructure.persistence.adapter;

import com.example.erp.notification.domain.delivery.NotificationDelivery;
import com.example.erp.notification.domain.delivery.repository.NotificationDeliveryRepository;
import com.example.erp.notification.infrastructure.persistence.jpa.NotificationDeliveryJpaEntity;
import com.example.erp.notification.infrastructure.persistence.jpa.NotificationDeliveryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationDeliveryRepositoryImpl implements NotificationDeliveryRepository {

    private final NotificationDeliveryJpaRepository jpa;

    @Override
    public void save(NotificationDelivery delivery) {
        NotificationDeliveryJpaEntity e = jpa.findById(delivery.id())
                .orElseGet(NotificationDeliveryJpaEntity::new);
        e.setId(delivery.id());
        e.setTenantId(delivery.tenantId());
        e.setNotificationId(delivery.notificationId());
        e.setEventId(delivery.eventId());
        e.setChannel(delivery.channel());
        e.setStatus(delivery.status());
        e.setAttemptCount(delivery.attemptCount());
        e.setScheduledRetryAt(delivery.scheduledRetryAt().orElse(null));
        e.setLastError(delivery.lastError().orElse(null));
        e.setVersion(delivery.version());
        e.setCreatedAt(delivery.createdAt());
        e.setUpdatedAt(delivery.updatedAt());
        jpa.save(e);
    }
}
