package com.example.notification.application.command;

import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.TemplateType;

public record CreateTemplateCommand(
        TemplateType type,
        NotificationChannel channel,
        String subject,
        String body
) {
}
