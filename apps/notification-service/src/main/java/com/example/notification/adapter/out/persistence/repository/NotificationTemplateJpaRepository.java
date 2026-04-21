package com.example.notification.adapter.out.persistence.repository;

import com.example.notification.adapter.out.persistence.entity.NotificationTemplateJpaEntity;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.TemplateType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface NotificationTemplateJpaRepository extends JpaRepository<NotificationTemplateJpaEntity, String> {
    Optional<NotificationTemplateJpaEntity> findByTypeAndChannel(TemplateType type, NotificationChannel channel);
    boolean existsByTypeAndChannel(TemplateType type, NotificationChannel channel);
}
