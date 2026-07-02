package com.example.notification.adapter.in.rest.dto.response;

import com.example.notification.application.result.TemplateSummaryResult;

/**
 * Response body for {@code GET /api/notifications/templates/summary}.
 * All counts are tenant-scoped KST calendar-period-to-date figures (TASK-BE-468).
 */
public record TemplateSummaryResponse(long today, long week, long month, long total) {

    public static TemplateSummaryResponse from(TemplateSummaryResult result) {
        return new TemplateSummaryResponse(result.today(), result.week(), result.month(), result.total());
    }
}
