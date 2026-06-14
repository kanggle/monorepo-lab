package com.example.notification.application.port.out;

import com.example.notification.domain.model.UserNotificationPreference;

import java.util.Optional;

public interface PreferenceRepository {
    UserNotificationPreference save(UserNotificationPreference preference);

    /** Tenant-scoped preference lookup (HTTP path; tenant from {@code TenantContext}). */
    Optional<UserNotificationPreference> findByUserId(String userId);

    /**
     * Tenant-scoped preference lookup with an explicit tenant (send path). The send path
     * runs on a Kafka thread with no {@code TenantContext}, so the bound event tenant is
     * passed explicitly.
     */
    Optional<UserNotificationPreference> findByUserId(String userId, String tenantId);
}
