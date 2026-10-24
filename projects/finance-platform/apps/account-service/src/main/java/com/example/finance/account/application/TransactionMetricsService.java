package com.example.finance.account.application;

import com.example.common.metrics.PerformanceMetrics;
import com.example.common.metrics.PerformanceMetrics.Metric;
import com.example.common.time.KstPeriodBounds;
import com.example.common.time.KstWindow;
import com.example.common.time.MetricsPeriod;
import com.example.finance.account.application.port.outbound.ClockPort;
import com.example.finance.account.domain.transaction.TransactionType;
import com.example.finance.account.domain.transaction.repository.SettledTransactionRow;
import com.example.finance.account.domain.transaction.repository.TransactionMetricsQuery;
import com.example.finance.account.domain.transaction.status.TransactionStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Overview performance metrics (TASK-MONO-812, ADR-MONO-084 P7) — see {@code account-api.md}
 * § GET /api/finance/accounts/metrics for the normative meaning of each key. Read-only;
 * authorization is the URL gate's ({@code GET /api/finance/**} read authorities), tenancy is
 * {@link ActorContext#tenantId()}.
 *
 * <p><b>What counts (ADR-MONO-084 D5 — money is counted only once it is settled).</b> A row
 * counts when its type is one of {@link #COUNTED_TYPES} and its current status is one of
 * {@link #SETTLED_STATUSES}, attributed to its {@code settledAt}. The exclusion is applied
 * <b>here</b>, not in the query, so the unit test's control group exercises it:
 * <ul>
 *   <li>{@code HOLD} — a reservation, not a movement. Its row is written COMPLETED the moment
 *       the hold is placed (the hold itself is what stays ACTIVE), so a status filter alone
 *       would count every hold. Excluded by type; the settled part reaches the metrics as the
 *       {@code CAPTURE} row, carrying the <em>captured</em> amount (a partial capture's row
 *       holds only the captured part — {@code AccountApplicationService#captureHold}).</li>
 *   <li>{@code RELEASE} (and an expiry, which writes no transaction row) — the reserved money
 *       going back; it never moved. Excluded by type.</li>
 *   <li>{@code FAILED} — never settled ({@code settled_at} stays null); excluded by status
 *       even if a future path stamped it.</li>
 *   <li>{@code TRANSFER} — ONE row (source account, counterparty on the same row), so a
 *       transfer is counted once, never as a debit plus a credit.</li>
 *   <li>{@code WITHDRAW} / {@code REVERSAL} — no v1 write path creates either (no HTTP mapping,
 *       no application call site); not reported. Whether a reversal subtracts is that future
 *       surface's contract decision, not guessed here.</li>
 * </ul>
 *
 * <p><b>Currencies (OD-3).</b> One money key per type per currency, never added together or
 * converted. The currency set is every currency a counted row carries in either window, the
 * same set for every type (a type with no activity in a currency reads {@code "0"}); with no
 * counted row at all it is {@value #DEFAULT_CURRENCY}, so the strip keeps its money cells.
 * Amounts are already integer minor units (F5) — no conversion, no rounding.
 */
@Service
@RequiredArgsConstructor
public class TransactionMetricsService {

    public static final String TOPUP_COUNT = "topupCount";
    public static final String TOPUP_AMOUNT_PREFIX = "topupAmount:";
    public static final String TRANSFER_COUNT = "transferCount";
    public static final String TRANSFER_AMOUNT_PREFIX = "transferAmount:";
    public static final String CAPTURE_COUNT = "captureCount";
    public static final String CAPTURE_AMOUNT_PREFIX = "captureAmount:";

    /** The money-moving kinds, in display order. */
    static final List<TransactionType> COUNTED_TYPES =
            List.of(TransactionType.TOPUP, TransactionType.TRANSFER, TransactionType.CAPTURE);

    /** Settled = the funds moved. FAILED / REVERSED / pre-settle states do not count. */
    static final Set<TransactionStatus> SETTLED_STATUSES =
            EnumSet.of(TransactionStatus.SETTLED, TransactionStatus.COMPLETED);

    /** Shown when neither window has a counted row, so the strip keeps its money cells. */
    static final String DEFAULT_CURRENCY = "KRW";

    private final TransactionMetricsQuery metricsQuery;
    private final ClockPort clock;

    @Transactional(readOnly = true)
    public PerformanceMetrics getMetrics(ActorContext actor, MetricsPeriod period) {
        String tenantId = actor.tenantId();
        // KstPeriodBounds reads only the instant; the zone of this fixed clock is irrelevant.
        KstPeriodBounds bounds = KstPeriodBounds.from(Clock.fixed(clock.now(), ZoneOffset.UTC));
        KstWindow current = bounds.currentWindow(period);
        KstWindow previous = bounds.previousWindow(period);

        List<SettledTransactionRow> now = counted(
                metricsQuery.settledIn(tenantId, current.fromInstant(), current.toInstant()));
        List<SettledTransactionRow> before = counted(
                metricsQuery.settledIn(tenantId, previous.fromInstant(), previous.toInstant()));

        TreeSet<String> currencies = new TreeSet<>();
        now.forEach(r -> currencies.add(r.currency().code()));
        before.forEach(r -> currencies.add(r.currency().code()));
        if (currencies.isEmpty()) {
            currencies.add(DEFAULT_CURRENCY);
        }

        Map<String, Long> totalsNow = totals(now);
        Map<String, Long> totalsBefore = totals(before);

        List<Metric> metrics = new ArrayList<>();
        for (TransactionType type : COUNTED_TYPES) {
            String countKey = countKey(type);
            metrics.add(Metric.count(countKey,
                    totalsNow.getOrDefault(countKey, 0L), totalsBefore.getOrDefault(countKey, 0L)));
            for (String currency : currencies) {
                String amountKey = amountKey(type, currency);
                metrics.add(Metric.money(amountKey, currency,
                        totalsNow.getOrDefault(amountKey, 0L), totalsBefore.getOrDefault(amountKey, 0L)));
            }
        }

        return PerformanceMetrics.of(bounds, period, metrics, daily(now));
    }

    /** The inclusion rule — see the class javadoc. */
    private static List<SettledTransactionRow> counted(List<SettledTransactionRow> rows) {
        return rows.stream()
                .filter(r -> COUNTED_TYPES.contains(r.type()))
                .filter(r -> SETTLED_STATUSES.contains(r.status()))
                .filter(r -> r.settledAt() != null)
                .toList();
    }

    /** Per key: the count of each type, and each type's minor-unit sum per currency. */
    private static Map<String, Long> totals(List<SettledTransactionRow> rows) {
        Map<String, Long> totals = new HashMap<>();
        for (SettledTransactionRow r : rows) {
            add(totals, r);
        }
        return totals;
    }

    /** Per KST day of the settle instant ({@link KstPeriodBounds#dateOf}); missing keys zero-fill. */
    private static Map<LocalDate, Map<String, Long>> daily(List<SettledTransactionRow> rows) {
        Map<LocalDate, Map<String, Long>> daily = new HashMap<>();
        for (SettledTransactionRow r : rows) {
            add(daily.computeIfAbsent(KstPeriodBounds.dateOf(r.settledAt()), d -> new HashMap<>()), r);
        }
        return daily;
    }

    private static void add(Map<String, Long> into, SettledTransactionRow r) {
        into.merge(countKey(r.type()), 1L, Long::sum);
        // Math::addExact — an overflow is a loud failure, never a wrapped total.
        into.merge(amountKey(r.type(), r.currency().code()), r.amountMinor(), Math::addExact);
    }

    static String countKey(TransactionType type) {
        return switch (type) {
            case TOPUP -> TOPUP_COUNT;
            case TRANSFER -> TRANSFER_COUNT;
            case CAPTURE -> CAPTURE_COUNT;
            default -> throw new IllegalArgumentException("not a counted type: " + type);
        };
    }

    static String amountKey(TransactionType type, String currency) {
        String prefix = switch (type) {
            case TOPUP -> TOPUP_AMOUNT_PREFIX;
            case TRANSFER -> TRANSFER_AMOUNT_PREFIX;
            case CAPTURE -> CAPTURE_AMOUNT_PREFIX;
            default -> throw new IllegalArgumentException("not a counted type: " + type);
        };
        return prefix + currency;
    }
}
