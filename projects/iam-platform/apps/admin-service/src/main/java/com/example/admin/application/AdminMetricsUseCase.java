package com.example.admin.application;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.client.AccountServiceClient;
import com.example.admin.infrastructure.client.SecurityServiceClient;
import com.example.common.metrics.PerformanceMetrics;
import com.example.common.metrics.PerformanceMetrics.Metric;
import com.example.common.time.KstPeriodBounds;
import com.example.common.time.KstWindow;
import com.example.common.time.MetricsPeriod;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@code GET /api/admin/metrics} (TASK-MONO-811, ADR-MONO-084 P6) — the IAM overview «실적» strip. The
 * normative meaning of every key is {@code admin-api.md} § GET /api/admin/metrics; the shape and the
 * windows are {@code platform/contracts/performance-metrics-contract.md}.
 *
 * <p>The one console entry point (owner decision D-a): admin-service owns no login or account data, so it
 * asks the two services that do — security-service for {@code loginSuccess} / {@code loginFailure} /
 * {@code accountLocked}, account-service for {@code newAccount} — once for the current window and once for
 * the previous one, sums the days into the totals and keeps the current window's days as the series.
 * Each downstream answers counts per KST date only (no row ever reaches admin-service).
 *
 * <p><b>Authorization.</b> {@code @RequiresPermission(audit.read)} on the controller (same grade as
 * {@code GET /api/admin/audit}), then the same {@link QueryTenantScopeGate} as {@link AuditQueryUseCase}:
 * the tenant must be in the {@code audit.read} grant scope; {@code '*'} reaches every tenant.
 *
 * <p><b>No partial answer</b> (performance-metrics-contract.md § 4): a failure of either downstream — or an
 * answer that disagrees with the windows (a date outside the current window, an unparseable date) — fails
 * the whole call with {@link DownstreamFailureException} (503). A strip is never shown as «0» by default.
 *
 * <p><b>Audit.</b> A successful read writes no {@code admin_actions} row (BE-486 read-path rule — the
 * response is counts with no subject); a refusal writes the gate's / aspect's best-effort DENIED row with
 * {@link ActionCode#METRICS_QUERY}.
 *
 * <p>{@link Clock}: the service's single {@code Clock} bean ({@code OutboxConfig#outboxClock}), injected
 * unqualified; {@link KstPeriodBounds#from(Clock)} reads only its instant.
 */
@Service
@RequiredArgsConstructor
public class AdminMetricsUseCase {

    public static final String LOGIN_SUCCESS = "loginSuccess";
    public static final String LOGIN_FAILURE = "loginFailure";
    public static final String ACCOUNT_LOCKED = "accountLocked";
    public static final String NEW_ACCOUNT = "newAccount";

    private final QueryTenantScopeGate queryTenantScopeGate;
    private final SecurityServiceClient securityServiceClient;
    private final AccountServiceClient accountServiceClient;
    private final Clock clock;

    public PerformanceMetrics query(OperatorContext operator, MetricsPeriod period, String tenantId) {
        QueryTenantScopeGate.Resolved scope = queryTenantScopeGate.resolve(
                operator, tenantId, ActionCode.METRICS_QUERY, Permission.AUDIT_READ);
        String tenant = scope.tenantId();

        KstPeriodBounds bounds = KstPeriodBounds.from(clock);
        KstWindow current = bounds.currentWindow(period);
        KstWindow previous = bounds.previousWindow(period);

        SecurityServiceClient.SecurityMetricsDaily securityNow =
                securityServiceClient.loginMetricsDaily(tenant, current.fromInstant(), current.toInstant());
        SecurityServiceClient.SecurityMetricsDaily securityBefore =
                securityServiceClient.loginMetricsDaily(tenant, previous.fromInstant(), previous.toInstant());
        AccountServiceClient.AccountMetricsDaily accountsNow =
                accountServiceClient.newAccountMetricsDaily(tenant, current.fromInstant(), current.toInstant());
        AccountServiceClient.AccountMetricsDaily accountsBefore =
                accountServiceClient.newAccountMetricsDaily(tenant, previous.fromInstant(), previous.toInstant());

        Set<LocalDate> currentDates = new HashSet<>(bounds.currentDates(period));
        Map<LocalDate, Map<String, Long>> daily = new HashMap<>();
        for (SecurityServiceClient.SecurityMetricsDaily.Day d : securityNow.days()) {
            Map<String, Long> day = daily.computeIfAbsent(currentDate(d.date(), currentDates), k -> new HashMap<>());
            day.merge(LOGIN_SUCCESS, d.loginSuccess(), Long::sum);
            day.merge(LOGIN_FAILURE, d.loginFailure(), Long::sum);
            day.merge(ACCOUNT_LOCKED, d.accountLocked(), Long::sum);
        }
        for (AccountServiceClient.AccountMetricsDaily.Day d : accountsNow.days()) {
            daily.computeIfAbsent(currentDate(d.date(), currentDates), k -> new HashMap<>())
                    .merge(NEW_ACCOUNT, d.newAccount(), Long::sum);
        }

        List<Metric> metrics = List.of(
                Metric.count(LOGIN_SUCCESS,
                        sum(securityNow, SecurityServiceClient.SecurityMetricsDaily.Day::loginSuccess),
                        sum(securityBefore, SecurityServiceClient.SecurityMetricsDaily.Day::loginSuccess)),
                Metric.count(LOGIN_FAILURE,
                        sum(securityNow, SecurityServiceClient.SecurityMetricsDaily.Day::loginFailure),
                        sum(securityBefore, SecurityServiceClient.SecurityMetricsDaily.Day::loginFailure)),
                Metric.count(ACCOUNT_LOCKED,
                        sum(securityNow, SecurityServiceClient.SecurityMetricsDaily.Day::accountLocked),
                        sum(securityBefore, SecurityServiceClient.SecurityMetricsDaily.Day::accountLocked)),
                Metric.count(NEW_ACCOUNT, sum(accountsNow), sum(accountsBefore)));

        return PerformanceMetrics.of(bounds, period, metrics, daily);
    }

    /**
     * A downstream date of the current window. One outside it (or unparseable) means the downstream and
     * the windows disagree — a downstream fault (503), never the caller's 400 that
     * {@link PerformanceMetrics#of}'s {@code IllegalArgumentException} would otherwise become here.
     */
    private static LocalDate currentDate(String raw, Set<LocalDate> currentDates) {
        LocalDate date;
        try {
            date = LocalDate.parse(raw);
        } catch (DateTimeParseException | NullPointerException e) {
            throw new DownstreamFailureException("downstream metrics date is not yyyy-MM-dd: " + raw, e);
        }
        if (!currentDates.contains(date)) {
            throw new DownstreamFailureException("downstream metrics date outside the current window: " + date);
        }
        return date;
    }

    private static long sum(SecurityServiceClient.SecurityMetricsDaily body,
                            java.util.function.ToLongFunction<SecurityServiceClient.SecurityMetricsDaily.Day> key) {
        return body.days().stream().mapToLong(key).sum();
    }

    private static long sum(AccountServiceClient.AccountMetricsDaily body) {
        return body.days().stream().mapToLong(AccountServiceClient.AccountMetricsDaily.Day::newAccount).sum();
    }
}
