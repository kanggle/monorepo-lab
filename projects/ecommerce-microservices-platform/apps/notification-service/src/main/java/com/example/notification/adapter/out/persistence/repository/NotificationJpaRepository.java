package com.example.notification.adapter.out.persistence.repository;

import com.example.notification.adapter.out.persistence.entity.NotificationJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, String> {
    Page<NotificationJpaEntity> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    boolean existsByEventId(String eventId);
}
