package com.example.notification.application.port.out;

import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.NotificationTemplate;
import com.example.notification.domain.model.TemplateType;

import java.time.LocalDateTime;
import java.util.Optional;

public interface TemplateRepository {
    NotificationTemplate save(NotificationTemplate template);

    /**
     * Tenant-scoped admin template detail / update lookup (tenant from
     * {@code TenantContext}). Cross-tenant {@code templateId} → empty (caller 404s).
     */
    Optional<NotificationTemplate> findById(String templateId);

    /**
     * Tenant-scoped send-path template resolution. The send path runs on a Kafka thread
     * with no {@code TenantContext}, so the notification's bound tenant is passed
     * explicitly.
     */
    Optional<NotificationTemplate> findByTypeAndChannel(TemplateType type, NotificationChannel channel, String tenantId);

    /** Tenant-scoped admin create dedup (tenant from {@code TenantContext}). */
    boolean existsByTypeAndChannel(TemplateType type, NotificationChannel channel);

    /** Tenant-scoped admin template list (tenant from {@code TenantContext}). */
    PageResult<NotificationTemplate> findAll(PageQuery pageQuery);

    /** Tenant-scoped total count (tenant from {@code TenantContext}). */
    long countAllForTenant();

    /**
     * Tenant-scoped count of templates whose {@code createdAt} falls within
     * [{@code from}, {@code to}) (tenant from {@code TenantContext}).
     */
    long countCreatedBetween(LocalDateTime from, LocalDateTime to);
}
