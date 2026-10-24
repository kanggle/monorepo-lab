package com.example.account.application.service;

import com.example.account.application.port.AccountMetricsQueryPort;
import com.example.account.application.result.AccountMetricsDailyResult;
import com.example.account.domain.tenant.TenantId;
import com.example.common.time.KstPeriodBounds;
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
 * TASK-MONO-811 (ADR-MONO-084 P6) — {@code newAccount} per KST date for one half-open window
 * {@code [from, to)} ({@code admin-to-account.md} § GET /internal/account-metrics/daily;
 * the normative key definition is {@code admin-api.md} § GET /api/admin/metrics).
 *
 * <ul>
 *   <li><b>A concrete tenant X</b> (not the pool): accounts with {@code tenant_id = X} created in the
 *       window <b>plus</b> {@code consumer_site_memberships} rows with {@code site_tenant_id = X} whose
 *       {@code consented_at} is in the window — a pool consumer joining the site is a new account for
 *       that site (multi-tenancy.md § 소비자 계정 풀 § 6: that is when the site's {@code account.created}
 *       fires). Every status is counted on both sides: the row records that the account came into being
 *       (or joined), whatever happened to it afterwards.</li>
 *   <li><b>{@code consumer-pool}</b>: accounts rows only — the pool has no memberships of its own.</li>
 *   <li><b>{@code '*'}</b> (platform scope): accounts rows across every tenant only. A pool account is
 *       counted once, by its {@code accounts} row; memberships are <b>not</b> added (they would count the
 *       same person again per site).</li>
 * </ul>
 *
 * <p>The KST date is cut here with {@link KstPeriodBounds#dateOf}, never in SQL. admin-service computes
 * the windows and has already gated the tenant ({@code QueryTenantScopeGate}); this service trusts the
 * tenant it is given, like {@link AccountSearchQueryService}.
 */
@Service
@RequiredArgsConstructor
public class AccountMetricsQueryService {

    /** The platform-scope sentinel (mirrors admin-service {@code AdminOperator.PLATFORM_TENANT_ID}). */
    static final String ALL_TENANTS = "*";

    /** A metrics window is at most one month; this bounds an accidental unbounded scan. */
    static final Duration MAX_WINDOW = Duration.ofDays(62);

    private final AccountMetricsQueryPort metricsQueryPort;

    /**
     * @throws IllegalArgumentException blank tenant, missing bound, {@code to} before {@code from}, or
     *                                  a window longer than {@link #MAX_WINDOW} (→ 400 VALIDATION_ERROR)
     */
    @Transactional(readOnly = true)
    public AccountMetricsDailyResult daily(String tenantId, Instant from, Instant to) {
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

        List<Instant> counted = new ArrayList<>();
        if (ALL_TENANTS.equals(tenantId)) {
            counted.addAll(metricsQueryPort.accountsCreatedInAllTenants(from, to));
        } else {
            counted.addAll(metricsQueryPort.accountsCreatedIn(tenantId, from, to));
            if (!TenantId.CONSUMER_POOL.value().equals(tenantId)) {
                counted.addAll(metricsQueryPort.siteMembershipsConsentedIn(tenantId, from, to));
            }
        }

        Map<LocalDate, Long> byDay = new TreeMap<>();
        for (Instant at : counted) {
            byDay.merge(KstPeriodBounds.dateOf(at), 1L, Long::sum);
        }
        List<AccountMetricsDailyResult.Day> days = new ArrayList<>(byDay.size());
        byDay.forEach((date, n) -> days.add(new AccountMetricsDailyResult.Day(date, n)));
        return new AccountMetricsDailyResult(List.copyOf(days));
    }
}
