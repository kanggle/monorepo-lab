package com.example.erp.notification.infrastructure.persistence.adapter;

import com.example.erp.notification.domain.delivery.DeliveryStatus;
import com.example.erp.notification.domain.delivery.NotificationDelivery;
import com.example.erp.notification.domain.delivery.repository.NotificationDeliveryRepository;
import com.example.erp.notification.infrastructure.persistence.jpa.NotificationDeliveryJpaEntity;
import com.example.erp.notification.infrastructure.persistence.jpa.NotificationDeliveryJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

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

    @Override
    public Optional<NotificationDelivery> findById(String id) {
        return jpa.findById(id).map(NotificationDeliveryRepositoryImpl::toDomain);
    }

    @Override
    public List<String> findDueDeliveryIds(Instant now, int limit) {
        return jpa.findDueDeliveryIds(DeliveryStatus.PENDING, now, PageRequest.of(0, limit));
    }

    private static NotificationDelivery toDomain(NotificationDeliveryJpaEntity e) {
        return new NotificationDelivery(
                e.getId(), e.getTenantId(), e.getNotificationId(), e.getEventId(), e.getChannel(),
                NotificationDelivery.DEFAULT_MAX_ATTEMPTS, e.getStatus(), e.getAttemptCount(),
                e.getScheduledRetryAt(), e.getLastError(), e.getVersion(),
                e.getCreatedAt(), e.getUpdatedAt());
    }
}
