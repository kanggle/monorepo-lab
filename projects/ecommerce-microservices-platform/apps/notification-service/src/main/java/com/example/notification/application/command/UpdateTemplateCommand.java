package com.example.notification.application.command;

public record UpdateTemplateCommand(
        String templateId,
        String subject,
        String body
) {}
