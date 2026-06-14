package com.example.notification.application.port.in;

import com.example.notification.application.command.UpdatePreferenceCommand;
import com.example.notification.application.result.GetPreferenceResult;
import com.example.notification.domain.model.UserNotificationPreference;

public interface ManagePreferenceUseCase {
    GetPreferenceResult getPreference(String userId);
    GetPreferenceResult updatePreference(UpdatePreferenceCommand command);

    /** HTTP path: resolves the tenant from {@code TenantContext}. */
    UserNotificationPreference getOrCreatePreference(String userId);

    /**
     * Send path (TASK-BE-372 M4): resolves preferences for an explicit tenant bound from
     * the event envelope (the Kafka thread has no {@code TenantContext}). A created default
     * preference is stamped with that tenant.
     */
    UserNotificationPreference getOrCreatePreference(String userId, String tenantId);
}
