package com.example.notification.adapter.in.rest.dto.response;

import com.example.notification.application.result.GetPreferenceResult;

public record PreferenceResponse(
        String userId,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean pushEnabled
) {
    public static PreferenceResponse from(GetPreferenceResult result) {
        return new PreferenceResponse(
                result.userId(),
                result.emailEnabled(),
                result.smsEnabled(),
                result.pushEnabled()
        );
    }
}
