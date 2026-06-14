package com.example.notification.adapter.out.persistence.repository;

import com.example.notification.adapter.out.persistence.entity.NotificationJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface NotificationJpaRepository extends JpaRepository<NotificationJpaEntity, String> {

    /**
     * Tenant-scoped consumer "my notifications" list (TASK-BE-372 M3): the request
     * tenant (from {@code TenantContext}) plus the existing {@code user_id} owner guard.
     */
    Page<NotificationJpaEntity> findByTenantIdAndUserIdOrderByCreatedAtDesc(
            String tenantId, String userId, Pageable pageable);

    /**
     * Tenant-scoped single-notification lookup backing the consumer detail path. A
     * cross-tenant {@code notificationId} resolves to empty → caller 404s (existence
     * hidden, M3 cross-tenant-read-is-not-found).
     */
    Optional<NotificationJpaEntity> findByNotificationIdAndTenantId(String notificationId, String tenantId);

    /**
     * Tenant-scoped dedup (M3 system path): event ids are per-tenant, so the same event
     * id across two tenants must not collide. The send path passes the bound event tenant
     * explicitly (no {@code TenantContext} on the Kafka thread).
     */
    boolean existsByEventIdAndTenantId(String eventId, String tenantId);
}
