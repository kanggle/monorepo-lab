package com.example.notification.adapter.out.persistence.repository;

import com.example.notification.adapter.out.persistence.entity.NotificationTemplateJpaEntity;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.TemplateType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface NotificationTemplateJpaRepository extends JpaRepository<NotificationTemplateJpaEntity, String> {

    /**
     * Tenant-scoped admin template list (TASK-BE-372 M3) — backs the operator
     * "list templates" surface; excludes other tenants' rows.
     *
     * <p>Ordered newest-first ({@code createdAt DESC}) so a just-created template
     * lands on page 0 instead of at a random offset — {@code templateId} is a
     * random UUID, so without an explicit order a new row could fall onto a later
     * page and appear "missing" (TASK-BE-427). {@code templateId ASC} is a
     * deterministic tiebreaker that keeps pagination stable across requests when
     * rows share a {@code createdAt}. Mirrors the notification list's
     * {@code OrderByCreatedAtDesc}.
     */
    Page<NotificationTemplateJpaEntity> findByTenantIdOrderByCreatedAtDescTemplateIdAsc(
            String tenantId, Pageable pageable);

    /**
     * Tenant-scoped single-template lookup backing the admin detail / update path. A
     * cross-tenant {@code templateId} resolves to empty → caller 404s (existence hidden,
     * M3 cross-tenant-read-is-not-found).
     */
    Optional<NotificationTemplateJpaEntity> findByTemplateIdAndTenantId(String templateId, String tenantId);

    /**
     * Tenant-scoped send-path template resolution (M3 system path). The send path runs on
     * a Kafka thread with no {@code TenantContext}, so the notification's bound tenant is
     * passed explicitly.
     */
    Optional<NotificationTemplateJpaEntity> findByTypeAndChannelAndTenantId(
            TemplateType type, NotificationChannel channel, String tenantId);

    /**
     * Tenant-scoped admin create dedup (HTTP path; tenant from {@code TenantContext}). A
     * second tenant may own its own (type, channel) template.
     */
    boolean existsByTypeAndChannelAndTenantId(TemplateType type, NotificationChannel channel, String tenantId);
}
