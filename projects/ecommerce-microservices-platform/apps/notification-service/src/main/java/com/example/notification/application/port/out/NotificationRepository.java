package com.example.notification.application.port.out;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.notification.domain.model.Notification;

import java.util.Optional;

public interface NotificationRepository {
    Notification save(Notification notification);

    /** Tenant-scoped consumer notification detail (tenant from {@code TenantContext}). */
    Optional<Notification> findById(String notificationId);

    /** Tenant-scoped consumer "my notifications" list (tenant from {@code TenantContext}). */
    PageResult<Notification> findByUserId(String userId, PageQuery pageQuery);

    /**
     * Tenant-scoped dedup. The send path runs on a Kafka thread with no
     * {@code TenantContext}, so the bound event tenant is passed explicitly.
     */
    boolean existsByEventId(String eventId, String tenantId);
}
