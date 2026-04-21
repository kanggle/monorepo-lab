package com.example.notification.adapter.out.persistence.repository;

import com.example.notification.adapter.out.persistence.entity.UserNotificationPreferenceJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

interface UserNotificationPreferenceJpaRepository extends JpaRepository<UserNotificationPreferenceJpaEntity, String> {
}
