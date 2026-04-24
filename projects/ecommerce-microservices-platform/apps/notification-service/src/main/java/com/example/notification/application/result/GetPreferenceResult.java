package com.example.notification.application.result;

import com.example.notification.domain.model.UserNotificationPreference;

public record GetPreferenceResult(
        String userId,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean pushEnabled
) {
    public static GetPreferenceResult from(UserNotificationPreference preference) {
        return new GetPreferenceResult(
                preference.getUserId(),
                preference.isEmailEnabled(),
                preference.isSmsEnabled(),
                preference.isPushEnabled()
        );
    }
}
