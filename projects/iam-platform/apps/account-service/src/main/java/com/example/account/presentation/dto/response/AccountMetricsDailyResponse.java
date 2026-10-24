package com.example.account.presentation.dto.response;

import com.example.account.application.result.AccountMetricsDailyResult;

import java.time.Instant;
import java.util.List;

/**
 * TASK-MONO-811 — body of {@code GET /internal/account-metrics/daily} ({@code admin-to-account.md}).
 * Counts only: no account id, email or status.
 *
 * @param tenantId the tenant the counts cover, echoed ({@code "*"} = every tenant)
 * @param from     the window start, echoed (ISO-8601 instant, {@code Z})
 * @param to       the window end (exclusive), echoed
 * @param days     one entry per KST date with at least one counted row, ascending; absent = 0
 */
public record AccountMetricsDailyResponse(String tenantId, String from, String to, List<Day> days) {

    /** @param date {@code yyyy-MM-dd}, a KST date */
    public record Day(String date, long newAccount) {
    }

    public static AccountMetricsDailyResponse of(String tenantId, Instant from, Instant to,
                                                 AccountMetricsDailyResult result) {
        return new AccountMetricsDailyResponse(tenantId, from.toString(), to.toString(),
                result.days().stream().map(d -> new Day(d.date().toString(), d.newAccount())).toList());
    }
}
