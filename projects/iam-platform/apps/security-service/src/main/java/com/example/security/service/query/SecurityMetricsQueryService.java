package com.example.security.service.query;

import com.example.common.time.KstPeriodBounds;
import com.example.security.service.domain.history.LoginOutcome;
import com.example.security.service.infrastructure.persistence.AccountLockHistoryJpaRepository;
import com.example.security.service.infrastructure.persistence.LoginHistoryJpaRepository;
import com.example.security.service.infrastructure.persistence.LoginOutcomeAt;
import com.example.security.service.query.dto.SecurityMetricsDailyView;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * TASK-MONO-811 (ADR-MONO-084 P6) — the per-KST-date counts behind the IAM overview performance
 * metrics ({@code security-query-api.md} § GET /internal/security/metrics/daily). admin-service is
 * the only caller; it computes the windows ({@link KstPeriodBounds#currentWindow} /
 * {@code previousWindow}) and calls this once per window.
 *
 * <ul>
 *   <li>{@code loginSuccess} — {@code login_history} rows with {@code outcome = SUCCESS};</li>
 *   <li>{@code loginFailure} — rows with {@code outcome = FAILURE}. {@code ATTEMPTED},
 *       {@code RATE_LIMITED}, {@code TOKEN_REUSE} and {@code REFRESH} never move either login key —
 *       the query does not even read them;</li>
 *   <li>{@code accountLocked} — {@code account_lock_history} rows (one per {@code account.locked}).</li>
 * </ul>
 *
 * <p>Every row is attributed to its {@code occurred_at}; the window is half-open {@code [from, to)}.
 * The KST date is cut <b>here</b>, in Java, with {@link KstPeriodBounds#dateOf} — never in SQL
 * (performance-metrics-contract.md § 2).
 *
 * <p><b>Tenant filter (TASK-BE-653).</b> The login keys count a {@code login_history} row for tenant X
 * when {@code tenant_id = X} <b>or</b> {@code site_tenant_id = X}. A consumer-pool account's login is
 * recorded under {@code tenant_id = consumer-pool} (the account's own tenant) with
 * {@code site_tenant_id} = the site it logged in to, so it counts in that site's view AND in the
 * {@code consumer-pool} view — intended (admin-api.md § GET /api/admin/metrics). {@code '*'} reads every
 * row once, unfiltered. Rows written before TASK-BE-653 have no site (no backfill), so a site's pool
 * logins count only from that deployment on. {@code accountLocked} is unchanged — the
 * {@code account_lock_history} {@code tenant_id} only: a pool account's lock is not a site's
 * (owner decision, TASK-BE-653 AC-1), so a site's view does not count it.
 *
 * <p>Read-only, and reads only {@code (outcome, occurred_at)} / {@code (occurred_at)} — no PII column
 * is selected. Lives in {@code query/} like {@link SecurityQueryService} (architecture.md: the query
 * surface reads {@code infrastructure/persistence} directly and never calls {@code application}).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SecurityMetricsQueryService {

    /** The platform-scope sentinel admin-service forwards for a {@code '*'} operator. */
    public static final String ALL_TENANTS = "*";

    /**
     * Longest window accepted. A metrics window is at most one calendar month (31 days); the bound
     * is a guard against an accidental unbounded scan, not a product rule.
     */
    static final Duration MAX_WINDOW = Duration.ofDays(62);

    private static final List<String> COUNTED_OUTCOMES =
            List.of(LoginOutcome.SUCCESS.name(), LoginOutcome.FAILURE.name());

    private final LoginHistoryJpaRepository loginHistoryJpaRepository;
    private final AccountLockHistoryJpaRepository accountLockHistoryJpaRepository;

    /**
     * @throws IllegalArgumentException blank tenant, {@code to} before {@code from}, or a window
     *                                  longer than {@link #MAX_WINDOW} (→ 400 VALIDATION_ERROR)
     */
    public SecurityMetricsDailyView daily(String tenantId, Instant from, Instant to) {
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId is required");
        }
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must not be before from");
        }
        if (Duration.between(from, to).compareTo(MAX_WINDOW) > 0) {
            throw new IllegalArgumentException("window must not exceed " + MAX_WINDOW.toDays() + " days");
        }

        boolean all = ALL_TENANTS.equals(tenantId);
        List<LoginOutcomeAt> logins = all
                ? loginHistoryJpaRepository.findOutcomesInWindowAllTenants(COUNTED_OUTCOMES, from, to)
                : loginHistoryJpaRepository.findOutcomesInWindow(tenantId, COUNTED_OUTCOMES, from, to);
        List<Instant> locks = all
                ? accountLockHistoryJpaRepository.findOccurredAtInWindowAllTenants(from, to)
                : accountLockHistoryJpaRepository.findOccurredAtInWindow(tenantId, from, to);

        Map<LocalDate, long[]> byDay = new TreeMap<>();
        for (LoginOutcomeAt row : logins) {
            long[] day = byDay.computeIfAbsent(KstPeriodBounds.dateOf(row.occurredAt()), d -> new long[3]);
            if (LoginOutcome.SUCCESS.name().equals(row.outcome())) {
                day[0]++;
            } else if (LoginOutcome.FAILURE.name().equals(row.outcome())) {
                day[1]++;
            }
        }
        for (Instant at : locks) {
            byDay.computeIfAbsent(KstPeriodBounds.dateOf(at), d -> new long[3])[2]++;
        }

        List<SecurityMetricsDailyView.Day> days = new ArrayList<>(byDay.size());
        byDay.forEach((date, c) -> days.add(new SecurityMetricsDailyView.Day(date.toString(), c[0], c[1], c[2])));
        return new SecurityMetricsDailyView(tenantId, from.toString(), to.toString(), List.copyOf(days));
    }
}
