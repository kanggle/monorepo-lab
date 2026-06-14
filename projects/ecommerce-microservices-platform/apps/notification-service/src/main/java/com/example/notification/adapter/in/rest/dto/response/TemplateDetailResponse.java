package com.example.notification.adapter.in.rest.dto.response;

import com.example.notification.domain.model.NotificationTemplate;

/**
 * Single-template detail response (TASK-BE-372 gap-fill). Unlike
 * {@link TemplateListResponse.TemplateSummary} (which omits {@code body} for the list
 * view), this carries the full template — including {@code body} — for the admin /
 * console template-edit page. Field shape mirrors what the platform-console
 * template-edit surface's {@code getTemplate} consumes.
 */
public record TemplateDetailResponse(
        String templateId,
        String type,
        String channel,
        String subject,
        String body,
        String createdAt,
        String updatedAt
) {
    public static TemplateDetailResponse from(NotificationTemplate t) {
        return new TemplateDetailResponse(
                t.getTemplateId(),
                t.getType().name(),
                t.getChannel().name(),
                t.getSubject(),
                t.getBody(),
                t.getCreatedAt().toString(),
                t.getUpdatedAt().toString()
        );
    }
}
