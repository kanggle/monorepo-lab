package com.example.notification.domain.model;

import java.time.LocalDateTime;

public class UserNotificationPreference {

    private String userId;
    private boolean emailEnabled;
    private boolean smsEnabled;
    private boolean pushEnabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    private UserNotificationPreference() {
    }

    public static UserNotificationPreference createDefault(String userId) {
        UserNotificationPreference pref = new UserNotificationPreference();
        pref.userId = userId;
        pref.emailEnabled = true;
        pref.smsEnabled = false;
        pref.pushEnabled = true;
        pref.createdAt = LocalDateTime.now();
        pref.updatedAt = LocalDateTime.now();
        return pref;
    }

    public static UserNotificationPreference reconstitute(String userId,
                                                           boolean emailEnabled,
                                                           boolean smsEnabled,
                                                           boolean pushEnabled,
                                                           LocalDateTime createdAt,
                                                           LocalDateTime updatedAt) {
        UserNotificationPreference pref = new UserNotificationPreference();
        pref.userId = userId;
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
