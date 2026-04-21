package com.example.notification.application.port.in;

import com.example.notification.application.command.UpdatePreferenceCommand;
import com.example.notification.application.result.GetPreferenceResult;
import com.example.notification.domain.model.UserNotificationPreference;

public interface ManagePreferenceUseCase {
    GetPreferenceResult getPreference(String userId);
    GetPreferenceResult updatePreference(UpdatePreferenceCommand command);
    UserNotificationPreference getOrCreatePreference(String userId);
}
