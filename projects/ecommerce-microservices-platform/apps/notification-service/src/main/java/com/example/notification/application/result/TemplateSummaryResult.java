package com.example.notification.application.result;

/**
 * KST calendar-period-to-date counts of notification templates for the current tenant
 * (TASK-BE-468).
 */
public record TemplateSummaryResult(long today, long week, long month, long total) {}
