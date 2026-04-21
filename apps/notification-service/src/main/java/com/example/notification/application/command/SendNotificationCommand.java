package com.example.notification.application.command;

import com.example.notification.domain.model.TemplateType;

import java.util.Map;

public record SendNotificationCommand(
        String userId,
        String eventId,
        TemplateType templateType,
        Map<String, String> variables
) {}
