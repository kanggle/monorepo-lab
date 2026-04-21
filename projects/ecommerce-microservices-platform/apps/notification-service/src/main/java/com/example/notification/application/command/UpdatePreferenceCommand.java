package com.example.notification.application.command;

public record UpdatePreferenceCommand(
        String userId,
        boolean emailEnabled,
        boolean smsEnabled,
        boolean pushEnabled
) {}
