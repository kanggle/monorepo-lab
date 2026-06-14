package com.example.notification.domain.model;

import com.example.notification.domain.tenant.TenantContext;

import java.time.LocalDateTime;

public class UserNotificationPreference {

    private String userId;
    private String tenantId;
    private boolean emailEnabled;
    private boolean smsEnabled;
    private boolean pushEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private UserNotificationPreference() {
    }

    /**
     * Default preference bound to the current request's tenant (HTTP path). An unset
     * {@link TenantContext} resolves to the default tenant (D8 net-zero).
     */
    public static UserNotificationPreference createDefault(String userId) {
        return createDefault(userId, TenantContext.currentTenant());
    }

    /**
     * Default preference bound to an explicit tenant (TASK-BE-372 M4). Used by the send
     * path, which runs on a Kafka thread with no HTTP context and threads the originating
     * event tenant explicitly; a blank/null value resolves to the default tenant.
     */
    public static UserNotificationPreference createDefault(String userId, String tenantId) {
        UserNotificationPreference pref = new UserNotificationPreference();
        pref.userId = userId;
        pref.tenantId = TenantContext.resolveOrDefault(tenantId);
        pref.emailEnabled = true;
        pref.smsEnabled = false;
        pref.pushEnabled = true;
        pref.createdAt = LocalDateTime.now();
        pref.updatedAt = LocalDateTime.now();
        return pref;
    }

    public static UserNotificationPreference reconstitute(String userId,
                                                           String tenantId,
                                                           boolean emailEnabled,
                                                           boolean smsEnabled,
                                                           boolean pushEnabled,
                                                           LocalDateTime createdAt,
                                                           LocalDateTime updatedAt) {
        UserNotificationPreference pref = new UserNotificationPreference();
        pref.userId = userId;
        pref.tenantId = tenantId;
        pref.emailEnabled = emailEnabled;
        pref.smsEnabled = smsEnabled;
        pref.pushEnabled = pushEnabled;
        pref.createdAt = createdAt;
        pref.updatedAt = updatedAt;
        return pref;
    }

    public void update(boolean emailEnabled, boolean smsEnabled, boolean pushEnabled) {
        this.emailEnabled = emailEnabled;
        this.smsEnabled = smsEnabled;
        this.pushEnabled = pushEnabled;
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isChannelEnabled(NotificationChannel channel) {
        return switch (channel) {
            case EMAIL -> emailEnabled;
            case SMS -> smsEnabled;
            case PUSH -> pushEnabled;
        };
    }

    public String getUserId() {
        return userId;
    }

    public String getTenantId() {
        return tenantId;
    }

    public boolean isEmailEnabled() {
        return emailEnabled;
    }

    public boolean isSmsEnabled() {
        return smsEnabled;
    }

    public boolean isPushEnabled() {
        return pushEnabled;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
