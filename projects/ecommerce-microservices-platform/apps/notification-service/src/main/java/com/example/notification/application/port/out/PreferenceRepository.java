package com.example.notification.application.port.out;

import com.example.notification.domain.model.UserNotificationPreference;

import java.util.Optional;

public interface PreferenceRepository {
    UserNotificationPreference save(UserNotificationPreference preference);
    Optional<UserNotificationPreference> findByUserId(String userId);
}
