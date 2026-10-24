package com.example.account.application.result;

import java.time.LocalDate;
import java.util.List;

/**
 * TASK-MONO-811 — {@code newAccount} per KST date for one window
 * ({@code admin-to-account.md} § GET /internal/account-metrics/daily). Counts only.
 *
 * @param days one entry per KST date with at least one counted row, ascending
 */
public record AccountMetricsDailyResult(List<Day> days) {

    public record Day(LocalDate date, long newAccount) {
    }
}
