package com.example.erp.notification.infrastructure.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationDeliveryJpaRepository
        extends JpaRepository<NotificationDeliveryJpaEntity, String> {
}
