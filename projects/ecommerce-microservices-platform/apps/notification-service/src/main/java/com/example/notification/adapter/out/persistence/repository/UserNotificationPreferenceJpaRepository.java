package com.example.notification.adapter.out.persistence.repository;

import com.example.notification.adapter.out.persistence.entity.UserNotificationPreferenceJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface UserNotificationPreferenceJpaRepository extends JpaRepository<UserNotificationPreferenceJpaEntity, String> {

    /**
     * Tenant-scoped preference lookup (TASK-BE-372 M3): the bound tenant plus the
     * {@code user_id} key. The PK stays globally-unique {@code user_id}, but scoping the
     * read by tenant keeps a defensive boundary if the same user id ever spans tenants.
     */
    Optional<UserNotificationPreferenceJpaEntity> findByUserIdAndTenantId(String userId, String tenantId);
}
