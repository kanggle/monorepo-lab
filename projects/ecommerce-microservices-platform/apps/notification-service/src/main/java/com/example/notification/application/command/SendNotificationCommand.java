package com.example.notification.application.command;

import com.example.notification.domain.model.TemplateType;

import java.util.Map;

/**
 * Send-notification command (TASK-BE-372 M4). {@code tenantId} carries the originating
 * tenant bound from the inbound event envelope (default {@code "ecommerce"} when absent),
 * threaded explicitly because the send path runs on a Kafka thread with no HTTP
 * {@code TenantContext}: the created notification, the template resolution and the dedup
 * all scope to this tenant.
 */
public record SendNotificationCommand(
        String tenantId,
        String userId,
        String eventId,
        TemplateType templateType,
        Map<String, String> variables
) {}
