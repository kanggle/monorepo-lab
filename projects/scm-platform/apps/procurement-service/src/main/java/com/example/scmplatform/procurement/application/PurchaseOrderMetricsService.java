package com.example.scmplatform.procurement.application;

import com.example.common.metrics.PerformanceMetrics;
import com.example.common.metrics.PerformanceMetrics.Metric;
import com.example.common.time.KstPeriodBounds;
import com.example.common.time.KstWindow;
import com.example.common.time.MetricsPeriod;
import com.example.scmplatform.procurement.domain.po.repository.PurchaseOrderMetricsQuery;
import com.example.scmplatform.procurement.domain.po.repository.ReceivedTransitionRow;
import com.example.scmplatform.procurement.domain.po.repository.SubmittedPoRow;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Currency;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Overview performance metrics (TASK-MONO-810, ADR-MONO-084 P5) — see
 * {@code procurement-api.md} § GET /api/procurement/po/metrics for the normative meaning of
 * each key. Read-only; authorization is the URL gate's ({@code GET /api/procurement/**}),
 * tenancy is {@link ActorContext#tenantId()}.
 *
 * <ul>
 *   <li>{@code poCount} / {@code poAmount:<CUR>} — POs submitted in the window whose current
 *       status is not {@code CANCELED}. The exclusion is applied <b>here</b>, not in the query.</li>
 *   <li>{@code poAmount:<CUR>} — one key per currency, never converted; the per-currency
 *       decimal sum is converted to minor units <b>once</b> (HALF_UP), so per-PO rounding cannot
 *       drift the total.</li>
 *   <li>{@code receivedCount} — distinct POs with a transition to {@code RECEIVED} in the
 *       window, attributed to the transition time.</li>
 * </ul>
 *
 * <p>{@link Clock}: the service's single {@code Clock} bean ({@code OutboxConfig#outboxClock},
 * system UTC) — injected unqualified, as {@code WebhookSecurityConfig} requires (a second
 * {@code Clock} bean would make every unqualified injection ambiguous). The zone is irrelevant:
 * {@link KstPeriodBounds#from(Clock)} reads only the instant.
 */
@Service
@RequiredArgsConstructor
public class PurchaseOrderMetricsService {

    public static final String METRIC_PO_COUNT = "poCount";
    public static final String METRIC_PO_AMOUNT_PREFIX = "poAmount:";
    public static final String METRIC_RECEIVED_COUNT = "receivedCount";

    /** Shown when neither window has a submitted PO, so the strip keeps its money cell. */
    static final String DEFAULT_CURRENCY = "KRW";

    /** Fraction digits for a code the JDK does not know as ISO-4217 (contract § poAmount). */
    static final int NON_ISO_FRACTION_DIGITS = 2;

    private final PurchaseOrderMetricsQuery metricsQuery;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PerformanceMetrics getMetrics(ActorContext actor, MetricsPeriod period) {
        String tenantId = actor.tenantId();
        KstPeriodBounds bounds = KstPeriodBounds.from(clock);
        KstWindow current = bounds.currentWindow(period);
        KstWindow previous = bounds.previousWindow(period);

        List<SubmittedPoRow> submittedNow = counted(
                metricsQuery.submittedIn(tenantId, current.fromInstant(), current.toInstant()));
        List<SubmittedPoRow> submittedBefore = counted(
                metricsQuery.submittedIn(tenantId, previous.fromInstant(), previous.toInstant()));
        Map<String, Instant> receivedNow = firstReceiptPerPo(
                metricsQuery.receivedIn(tenantId, current.fromInstant(), current.toInstant()));
        Map<String, Instant> receivedBefore = firstReceiptPerPo(
                metricsQuery.receivedIn(tenantId, previous.fromInstant(), previous.toInstant()));

        Map<String, BigDecimal> amountNow = sumByCurrency(submittedNow);
        Map<String, BigDecimal> amountBefore = sumByCurrency(submittedBefore);
        TreeSet<String> currencies = new TreeSet<>(amountNow.keySet());
        currencies.addAll(amountBefore.keySet());
        if (currencies.isEmpty()) {
            currencies.add(DEFAULT_CURRENCY);
        }

        List<Metric> metrics = new ArrayList<>();
        metrics.add(Metric.count(METRIC_PO_COUNT, submittedNow.size(), submittedBefore.size()));
        for (String currency : currencies) {
            metrics.add(Metric.money(METRIC_PO_AMOUNT_PREFIX + currency, currency,
                    toMinorUnits(amountNow.getOrDefault(currency, BigDecimal.ZERO), currency),
                    toMinorUnits(amountBefore.getOrDefault(currency, BigDecimal.ZERO), currency)));
        }
        metrics.add(Metric.count(METRIC_RECEIVED_COUNT, receivedNow.size(), receivedBefore.size()));

        return PerformanceMetrics.of(bounds, period, metrics,
                daily(submittedNow, receivedNow));
    }

    /** {@code poCount} membership: the current status is not {@code CANCELED}. */
    private static List<SubmittedPoRow> counted(List<SubmittedPoRow> rows) {
        return rows.stream().filter(r -> r.status() != PoStatus.CANCELED).toList();
    }

    /** One entry per PO — its earliest transition to RECEIVED inside the window. */
    private static Map<String, Instant> firstReceiptPerPo(List<ReceivedTransitionRow> rows) {
        Map<String, Instant> first = new HashMap<>();
        for (ReceivedTransitionRow r : rows) {
            first.merge(r.poId(), r.occurredAt(), (a, b) -> a.isBefore(b) ? a : b);
        }
        return first;
    }

    private static Map<String, BigDecimal> sumByCurrency(List<SubmittedPoRow> rows) {
        Map<String, BigDecimal> sums = new TreeMap<>();
        for (SubmittedPoRow r : rows) {
            sums.merge(r.currency(), r.totalAmount(), BigDecimal::add);
        }
        return sums;
    }

    /**
     * Series: per KST day, the count, each currency's decimal sum converted with the same rule
     * as the total (so a day rounds on its own — contract § GET /po/metrics, «Series»), and the
     * received count. Keys absent on a day are zero-filled by {@link PerformanceMetrics#of}.
     */
    private static Map<LocalDate, Map<String, Long>> daily(List<SubmittedPoRow> submitted,
                                                          Map<String, Instant> received) {
        Map<LocalDate, Map<String, BigDecimal>> amountByDay = new HashMap<>();
        Map<LocalDate, Map<String, Long>> daily = new HashMap<>();
        for (SubmittedPoRow r : submitted) {
            LocalDate day = KstPeriodBounds.dateOf(r.submittedAt());
            daily.computeIfAbsent(day, d -> new HashMap<>()).merge(METRIC_PO_COUNT, 1L, Long::sum);
            amountByDay.computeIfAbsent(day, d -> new HashMap<>())
                    .merge(r.currency(), r.totalAmount(), BigDecimal::add);
        }
        amountByDay.forEach((day, sums) -> sums.forEach((currency, sum) ->
                daily.computeIfAbsent(day, d -> new HashMap<>())
                        .put(METRIC_PO_AMOUNT_PREFIX + currency, toMinorUnits(sum, currency))));
        for (Instant at : received.values()) {
            daily.computeIfAbsent(KstPeriodBounds.dateOf(at), d -> new HashMap<>())
                    .merge(METRIC_RECEIVED_COUNT, 1L, Long::sum);
        }
        return daily;
    }

    /**
     * A decimal amount in {@code currency} → integer minor units: ISO-4217 default fraction
     * digits ({@code Currency#getDefaultFractionDigits}); a code the JDK does not know, or one
     * without a minor unit defined ({@code -1}), uses {@value #NON_ISO_FRACTION_DIGITS}.
     * HALF_UP. {@code longValueExact} — an overflow is a loud failure, never a wrapped number.
     */
    static long toMinorUnits(BigDecimal amount, String currency) {
        return amount.movePointRight(fractionDigits(currency))
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    static int fractionDigits(String currency) {
        try {
            int digits = Currency.getInstance(currency).getDefaultFractionDigits();
            return digits < 0 ? NON_ISO_FRACTION_DIGITS : digits;
        } catch (IllegalArgumentException | NullPointerException notIso) {
            return NON_ISO_FRACTION_DIGITS;
        }
    }
}
