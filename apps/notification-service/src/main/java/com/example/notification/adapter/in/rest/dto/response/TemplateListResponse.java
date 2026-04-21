package com.example.notification.adapter.in.rest.dto.response;

import com.example.notification.application.page.PageResult;
import com.example.notification.domain.model.NotificationTemplate;

import java.util.List;

public record TemplateListResponse(
        List<TemplateSummary> content,
        int page,
        int size,
        long totalElements
) {
    public record TemplateSummary(
            String templateId,
            String type,
            String channel,
            String subject,
            String createdAt
    ) {
        public static TemplateSummary from(NotificationTemplate t) {
            return new TemplateSummary(
                    t.getTemplateId(),
                    t.getType().name(),
                    t.getChannel().name(),
                    t.getSubject(),
                    t.getCreatedAt().toString()
            );
        }
    }

    public static TemplateListResponse from(PageResult<NotificationTemplate> pageResult) {
        return new TemplateListResponse(
                pageResult.content().stream().map(TemplateSummary::from).toList(),
                pageResult.page(),
                pageResult.size(),
                pageResult.totalElements()
        );
    }
}
