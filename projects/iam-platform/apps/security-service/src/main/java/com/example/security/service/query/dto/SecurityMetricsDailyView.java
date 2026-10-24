package com.example.security.service.query.dto;

import java.util.List;

/**
 * TASK-MONO-811 — response of {@code GET /internal/security/metrics/daily}
 * ({@code security-query-api.md}). Counts only: no account id, IP, fingerprint or row of any kind.
 *
 * @param tenantId the tenant the counts cover, echoed ({@code "*"} = every tenant)
 * @param from     the window start, echoed (ISO-8601 instant)
 * @param to       the window end (exclusive), echoed
 * @param days     one entry per KST date that had at least one counted row, ascending; a date
 *                 with nothing is absent (the caller zero-fills)
 */
public record SecurityMetricsDailyView(String tenantId, String from, String to, List<Day> days) {

    /**
     * One KST calendar date.
     *
     * @param date          {@code yyyy-MM-dd}, the KST date ({@code KstPeriodBounds.dateOf})
     * @param loginSuccess  {@code login_history} rows with {@code outcome = SUCCESS}
     * @param loginFailure  {@code login_history} rows with {@code outcome = FAILURE}
     * @param accountLocked {@code account_lock_history} rows
     */
    public record Day(String date, long loginSuccess, long loginFailure, long accountLocked) {
    }
}
