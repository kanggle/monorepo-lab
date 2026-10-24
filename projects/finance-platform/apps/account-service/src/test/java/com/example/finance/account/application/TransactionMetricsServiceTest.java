package com.example.finance.account.application;

import com.example.common.metrics.PerformanceMetrics;
import com.example.common.time.MetricsPeriod;
import com.example.finance.account.domain.transaction.TransactionType;
import com.example.finance.account.domain.transaction.repository.SettledTransactionRow;
import com.example.finance.account.domain.transaction.repository.TransactionMetricsQuery;
import com.example.finance.account.domain.transaction.status.TransactionStatus;
import com.example.finance.common.money.Currency;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link TransactionMetricsService} (TASK-MONO-812, ADR-MONO-084 P7) — the D5 inclusion rule
 * (settled money only: holds and releases never move the amounts, a capture moves by its
 * captured amount), per-currency keys never summed (OD-3), KST windows and day bucketing under
 * either JVM default zone, the zero-filled series. Fixed clock: Fri 2026-10-23 14:05 KST.
 *
 * <p>Week window: [Mon 10-19 00:00 KST = 10-18T15:00Z, now = 10-23T05:05Z). Previous (same
 * elapsed time): [10-11T15:00Z, 10-16T05:05Z).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class TransactionMetricsServiceTest {

    private static final String TENANT = "finance";
    private static final Instant NOW = Instant.parse("2026-10-23T05:05:00Z");
    private static final Instant CUR_FROM = Instant.parse("2026-10-18T15:00:00Z");
    private static final Instant PREV_FROM = Instant.parse("2026-10-11T15:00:00Z");
    private static final Instant PREV_TO = Instant.parse("2026-10-16T05:05:00Z");

    private static final ActorContext READER =
            new ActorContext("op-r", TENANT, Set.of("FINANCE_OPERATOR", "FINANCE_ACCOUNT_READ"));

    @Mock
    TransactionMetricsQuery metricsQuery;

    private TransactionMetricsService service;
    private TimeZone originalZone;

    @BeforeEach
    void setUp() {
        originalZone = TimeZone.getDefault();
        service = new TransactionMetricsService(metricsQuery, () -> NOW);
    }

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    // ---- helpers -----------------------------------------------------------

    private void stubWeek(List<SettledTransactionRow> current, List<SettledTransactionRow> previous) {
        when(metricsQuery.settledIn(TENANT, CUR_FROM, NOW)).thenReturn(current);
        when(metricsQuery.settledIn(TENANT, PREV_FROM, PREV_TO)).thenReturn(previous);
    }

    private static SettledTransactionRow row(TransactionType type, TransactionStatus status,
                                             long minor, Currency currency, String settledAt) {
        return new SettledTransactionRow(type, status, minor, currency, Instant.parse(settledAt));
    }

    private static SettledTransactionRow completed(TransactionType type, long minor, Currency currency,
                                                   String settledAt) {
        return row(type, TransactionStatus.COMPLETED, minor, currency, settledAt);
    }

    private static PerformanceMetrics.Metric metric(PerformanceMetrics m, String key) {
        return m.metrics().stream().filter(x -> x.key().equals(key)).findFirst()
                .orElseThrow(() -> new AssertionError("no metric " + key + " in " + m.metrics()));
    }

    private static String day(PerformanceMetrics m, String date, String key) {
        return m.series().stream().filter(p -> p.date().equals(date)).findFirst()
                .map(p -> p.values().get(key)).orElseThrow();
    }

    // ---- windows & shape -----------------------------------------------------

    @Test
    @DisplayName("week: current [Mon 00:00 KST, now) vs previous of the same elapsed time; key order = type, then its currencies")
    void windowsAndKeyOrder() {
        stubWeek(
                List.of(completed(TransactionType.TOPUP, 150_000, Currency.KRW, "2026-10-19T01:00:00Z"),
                        completed(TransactionType.TRANSFER, 50_000, Currency.KRW, "2026-10-20T01:00:00Z"),
                        completed(TransactionType.CAPTURE, 30_000, Currency.KRW, "2026-10-21T01:00:00Z")),
                List.of(completed(TransactionType.TOPUP, 70_000, Currency.KRW, "2026-10-12T01:00:00Z")));

        PerformanceMetrics m = service.getMetrics(READER, MetricsPeriod.WEEK);

        assertThat(m.metrics()).extracting(PerformanceMetrics.Metric::key).containsExactly(
                "topupCount", "topupAmount:KRW",
                "transferCount", "transferAmount:KRW",
                "captureCount", "captureAmount:KRW");
        assertThat(metric(m, "topupCount").current()).isEqualTo("1");
        assertThat(metric(m, "topupCount").previous()).isEqualTo("1");
        assertThat(metric(m, "topupCount").unit()).isEqualTo("count");
        assertThat(metric(m, "topupCount").currency()).isNull();
        assertThat(metric(m, "topupAmount:KRW").current()).isEqualTo("150000");
        assertThat(metric(m, "topupAmount:KRW").previous()).isEqualTo("70000");
        assertThat(metric(m, "topupAmount:KRW").unit()).isEqualTo("money");
        assertThat(metric(m, "topupAmount:KRW").currency()).isEqualTo("KRW");
        assertThat(metric(m, "transferAmount:KRW").current()).isEqualTo("50000");
        assertThat(metric(m, "captureAmount:KRW").current()).isEqualTo("30000");
        assertThat(m.current().from()).isEqualTo("2026-10-19T00:00:00+09:00");
        assertThat(m.current().to()).isEqualTo("2026-10-23T14:05:00+09:00");
        assertThat(m.previous().from()).isEqualTo("2026-10-12T00:00:00+09:00");
        assertThat(m.previous().to()).isEqualTo("2026-10-16T14:05:00+09:00");
        assertThat(m.timezone()).isEqualTo("Asia/Seoul");
        assertThat(m.period()).isEqualTo("week");
    }

    @Test
    @DisplayName("month on Mar 30: previous window stops at Mar 1 (short February), queried with those exact bounds")
    void monthAcrossShortMonth() {
        Instant now = Instant.parse("2026-03-30T03:00:00Z");        // 2026-03-30 12:00 KST
        Instant curFrom = Instant.parse("2026-02-28T15:00:00Z");    // Mar 1 00:00 KST
        Instant prevFrom = Instant.parse("2026-01-31T15:00:00Z");   // Feb 1 00:00 KST
        service = new TransactionMetricsService(metricsQuery, () -> now);
        when(metricsQuery.settledIn(TENANT, curFrom, now)).thenReturn(List.of());
        when(metricsQuery.settledIn(TENANT, prevFrom, curFrom)).thenReturn(List.of());

        PerformanceMetrics m = service.getMetrics(READER, MetricsPeriod.MONTH);

        assertThat(m.previous().from()).isEqualTo("2026-02-01T00:00:00+09:00");
        assertThat(m.previous().to()).isEqualTo("2026-03-01T00:00:00+09:00");
        assertThat(m.series()).hasSize(30);
        verify(metricsQuery).settledIn(TENANT, prevFrom, curFrom);
    }

    // ---- AC-1 control group ------------------------------------------------------

    @Test
    @DisplayName("🔴 AC-1 control: active hold · released hold · failed txn move nothing; a capture moves by its captured amount only")
    void holdReleaseFailedExcludedCaptureCounted() {
        stubWeek(
                List.of(
                        // An active hold: its HOLD row is written COMPLETED the moment it is placed.
                        completed(TransactionType.HOLD, 100_000, Currency.KRW, "2026-10-19T01:00:00Z"),
                        // A released hold: HOLD row + RELEASE row, both COMPLETED.
                        completed(TransactionType.HOLD, 40_000, Currency.KRW, "2026-10-19T02:00:00Z"),
                        completed(TransactionType.RELEASE, 40_000, Currency.KRW, "2026-10-19T03:00:00Z"),
                        // A failed top-up (a FAILED row with a settle stamp cannot happen in v1 —
                        // it is here so the status leg of the rule is exercised on its own).
                        row(TransactionType.TOPUP, TransactionStatus.FAILED, 999_000, Currency.KRW,
                                "2026-10-19T04:00:00Z"),
                        // A partially captured hold: HOLD 80,000, CAPTURE row carries 30,000.
                        completed(TransactionType.HOLD, 80_000, Currency.KRW, "2026-10-20T01:00:00Z"),
                        completed(TransactionType.CAPTURE, 30_000, Currency.KRW, "2026-10-20T02:00:00Z")),
                List.of());

        PerformanceMetrics m = service.getMetrics(READER, MetricsPeriod.WEEK);

        assertThat(metric(m, "captureCount").current()).isEqualTo("1");
        assertThat(metric(m, "captureAmount:KRW").current()).isEqualTo("30000");
        assertThat(metric(m, "topupCount").current()).isEqualTo("0");
        assertThat(metric(m, "topupAmount:KRW").current()).isEqualTo("0");
        assertThat(metric(m, "transferCount").current()).isEqualTo("0");
        assertThat(metric(m, "transferAmount:KRW").current()).isEqualTo("0");
        // No key for HOLD / RELEASE at all — a reservation is not a performance figure.
        assertThat(m.metrics()).extracting(PerformanceMetrics.Metric::key)
                .noneMatch(k -> k.toLowerCase().contains("hold") || k.toLowerCase().contains("release"));
        assertThat(day(m, "2026-10-20", "captureAmount:KRW")).isEqualTo("30000");
        assertThat(day(m, "2026-10-19", "captureAmount:KRW")).isEqualTo("0");
    }

    @Test
    @DisplayName("control: only SETTLED / COMPLETED count — REQUESTED · VALIDATED · AUTHORIZED · FAILED · REVERSED do not")
    void onlySettledStatusesCount() {
        List<SettledTransactionRow> rows = Arrays.stream(TransactionStatus.values())
                .map(s -> row(TransactionType.TOPUP, s, 1_000, Currency.KRW, "2026-10-19T01:00:00Z"))
                .toList();
        stubWeek(rows, List.of());

        PerformanceMetrics m = service.getMetrics(READER, MetricsPeriod.WEEK);

        assertThat(metric(m, "topupCount").current()).isEqualTo("2");
        assertThat(metric(m, "topupAmount:KRW").current()).isEqualTo("2000");
    }

    @Test
    @DisplayName("control: WITHDRAW / REVERSAL rows (no v1 write path) are not reported under any key")
    void withdrawAndReversalNotReported() {
        stubWeek(List.of(
                completed(TransactionType.WITHDRAW, 5_000, Currency.KRW, "2026-10-19T01:00:00Z"),
                completed(TransactionType.REVERSAL, 7_000, Currency.KRW, "2026-10-19T02:00:00Z")),
                List.of());

        PerformanceMetrics m = service.getMetrics(READER, MetricsPeriod.WEEK);

        assertThat(m.metrics()).allSatisfy(x -> assertThat(x.current()).isEqualTo("0"));
    }

    @Test
    @DisplayName("Edge: a hold placed last week and captured this week counts in THIS week, at the capture's settle time")
    void captureAttributedToCaptureTime() {
        stubWeek(
                List.of(completed(TransactionType.CAPTURE, 12_000, Currency.KRW, "2026-10-21T01:00:00Z")),
                // The hold itself settled last week — a HOLD row, which never counts.
                List.of(completed(TransactionType.HOLD, 12_000, Currency.KRW, "2026-10-13T01:00:00Z")));

        PerformanceMetrics m = service.getMetrics(READER, MetricsPeriod.WEEK);

        assertThat(metric(m, "captureCount").current()).isEqualTo("1");
        assertThat(metric(m, "captureCount").previous()).isEqualTo("0");
        assertThat(metric(m, "captureAmount:KRW").previous()).isEqualTo("0");
        assertThat(day(m, "2026-10-21", "captureCount")).isEqualTo("1");
    }

    @Test
    @DisplayName("Edge: a transfer is ONE row — counted once, its amount once (never debit + credit)")
    void transferCountedOnce() {
        stubWeek(List.of(completed(TransactionType.TRANSFER, 50_000, Currency.KRW, "2026-10-19T01:00:00Z")),
                List.of());

        PerformanceMetrics m = service.getMetrics(READER, MetricsPeriod.WEEK);

        assertThat(metric(m, "transferCount").current()).isEqualTo("1");
        assertThat(metric(m, "transferAmount:KRW").current()).isEqualTo("50000");
    }

    // ---- AC-2 currencies -------------------------------------------------------------

    @Test
    @DisplayName("🔴 AC-2: KRW and USD are separate keys, never added or converted; every type gets every seen currency")
    void currenciesSeparate() {
        stubWeek(
                List.of(completed(TransactionType.TOPUP, 150_000, Currency.KRW, "2026-10-19T01:00:00Z"),
                        completed(TransactionType.TOPUP, 2_550, Currency.USD, "2026-10-19T02:00:00Z"),
                        completed(TransactionType.CAPTURE, 1_000, Currency.USD, "2026-10-20T02:00:00Z")),
                List.of(completed(TransactionType.TOPUP, 10_000, Currency.KRW, "2026-10-12T01:00:00Z")));

        PerformanceMetrics m = service.getMetrics(READER, MetricsPeriod.WEEK);

        assertThat(m.metrics()).extracting(PerformanceMetrics.Metric::key).containsExactly(
                "topupCount", "topupAmount:KRW", "topupAmount:USD",
                "transferCount", "transferAmount:KRW", "transferAmount:USD",
                "captureCount", "captureAmount:KRW", "captureAmount:USD");
        assertThat(metric(m, "topupCount").current()).isEqualTo("2");
        assertThat(metric(m, "topupAmount:KRW").current()).isEqualTo("150000");
        // USD stays in cents and is never folded into KRW (150000 + 2550 = 152550 must not appear).
        assertThat(metric(m, "topupAmount:USD").current()).isEqualTo("2550");
        assertThat(metric(m, "topupAmount:USD").currency()).isEqualTo("USD");
        assertThat(metric(m, "topupAmount:USD").previous()).isEqualTo("0");
        assertThat(metric(m, "captureAmount:USD").current()).isEqualTo("1000");
        assertThat(metric(m, "captureAmount:KRW").current()).isEqualTo("0");
        assertThat(m.metrics()).extracting(PerformanceMetrics.Metric::current).doesNotContain("152550", "151000");
        assertThat(day(m, "2026-10-19", "topupAmount:USD")).isEqualTo("2550");
        assertThat(day(m, "2026-10-19", "topupAmount:KRW")).isEqualTo("150000");
    }

    @Test
    @DisplayName("no counted row in either window → zeros with the KRW money cells (the strip keeps its shape)")
    void emptyDefaultsToKrw() {
        stubWeek(List.of(completed(TransactionType.HOLD, 5_000, Currency.USD, "2026-10-19T01:00:00Z")),
                List.of());

        PerformanceMetrics m = service.getMetrics(READER, MetricsPeriod.WEEK);

        assertThat(m.metrics()).extracting(PerformanceMetrics.Metric::key).containsExactly(
                "topupCount", "topupAmount:KRW", "transferCount", "transferAmount:KRW",
                "captureCount", "captureAmount:KRW");
        assertThat(m.metrics()).allSatisfy(x -> assertThat(x.current()).isEqualTo("0"));
        assertThat(m.series()).hasSize(5);
    }

    // ---- AC-3 field census -------------------------------------------------------------

    @Test
    @DisplayName("🔴 AC-3: the row type carries no account / counterparty / hold id — nothing to leak")
    void rowCarriesNoAccountIdentity() {
        assertThat(Arrays.stream(SettledTransactionRow.class.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly("type", "status", "amountMinor", "currency", "settledAt");
    }

    // ---- AC-4 boundaries under either JVM default zone -------------------------------

    @ParameterizedTest(name = "JVM default zone {0}")
    @ValueSource(strings = {"UTC", "Asia/Seoul"})
    @DisplayName("🔴 AC-4: KST midnight cuts the day — Mon 00:00 KST is the first day; Thu 23:59:59 and Fri 00:00 KST land on different days")
    void kstMidnightBoundary(String zone) {
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        // The query is half-open on the exact bounds; a row exactly at CUR_FROM is returned by the
        // current-window query, one a second before it by the previous one — the service must
        // bucket them by KST date, not by the JVM zone.
        stubWeek(
                List.of(completed(TransactionType.TOPUP, 1_000, Currency.KRW, "2026-10-18T15:00:00Z"),   // Mon 00:00 KST
                        completed(TransactionType.TOPUP, 2_000, Currency.KRW, "2026-10-22T14:59:59Z"),   // Thu 23:59:59 KST
                        completed(TransactionType.TOPUP, 4_000, Currency.KRW, "2026-10-22T15:00:00Z")),  // Fri 00:00 KST
                List.of(completed(TransactionType.TOPUP, 8_000, Currency.KRW, "2026-10-11T15:00:00Z")));

        PerformanceMetrics m = service.getMetrics(READER, MetricsPeriod.WEEK);

        assertThat(m.series()).extracting(PerformanceMetrics.SeriesPoint::date).containsExactly(
                "2026-10-19", "2026-10-20", "2026-10-21", "2026-10-22", "2026-10-23");
        assertThat(day(m, "2026-10-19", "topupAmount:KRW")).isEqualTo("1000");
        assertThat(day(m, "2026-10-22", "topupAmount:KRW")).isEqualTo("2000");
        assertThat(day(m, "2026-10-23", "topupAmount:KRW")).isEqualTo("4000");
        assertThat(metric(m, "topupAmount:KRW").current()).isEqualTo("7000");
        assertThat(metric(m, "topupAmount:KRW").previous()).isEqualTo("8000");
        assertThat(m.current().from()).isEqualTo("2026-10-19T00:00:00+09:00");
    }

    @ParameterizedTest(name = "JVM default zone {0}")
    @ValueSource(strings = {"UTC", "Asia/Seoul"})
    @DisplayName("AC-4: at 00:30 KST Monday the week is that one day; the window is cut in KST whatever the JVM zone")
    void firstInstantOfWeek(String zone) {
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        Instant now = Instant.parse("2026-10-18T15:30:00Z");       // Mon 10-19 00:30 KST
        Instant curFrom = Instant.parse("2026-10-18T15:00:00Z");
        Instant prevFrom = Instant.parse("2026-10-11T15:00:00Z");
        Instant prevTo = Instant.parse("2026-10-11T15:30:00Z");
        service = new TransactionMetricsService(metricsQuery, () -> now);
        when(metricsQuery.settledIn(TENANT, curFrom, now)).thenReturn(List.of());
        when(metricsQuery.settledIn(TENANT, prevFrom, prevTo)).thenReturn(List.of());

        PerformanceMetrics m = service.getMetrics(READER, MetricsPeriod.WEEK);

        assertThat(m.series()).extracting(PerformanceMetrics.SeriesPoint::date).containsExactly("2026-10-19");
        verify(metricsQuery).settledIn(TENANT, curFrom, now);
    }

    @Test
    @DisplayName("AC-4 tenant: the query is asked for the actor's tenant only")
    void tenantFromActor() {
        ActorContext other = new ActorContext("op-x", "acme", Set.of("FINANCE_ACCOUNT_READ"));
        when(metricsQuery.settledIn("acme", CUR_FROM, NOW)).thenReturn(List.of());
        when(metricsQuery.settledIn("acme", PREV_FROM, PREV_TO)).thenReturn(List.of());

        service.getMetrics(other, MetricsPeriod.WEEK);

        verify(metricsQuery).settledIn("acme", CUR_FROM, NOW);
        verify(metricsQuery).settledIn("acme", PREV_FROM, PREV_TO);
    }

    @Test
    @DisplayName("series: every date of the current window, every key, zero-filled, summing to the totals")
    void seriesZeroFilledAndSums() {
        stubWeek(
                List.of(completed(TransactionType.TOPUP, 1_000, Currency.KRW, "2026-10-19T01:00:00Z"),
                        completed(TransactionType.TOPUP, 3_000, Currency.KRW, "2026-10-21T01:00:00Z"),
                        completed(TransactionType.TRANSFER, 500, Currency.KRW, "2026-10-21T02:00:00Z")),
                List.of());

        PerformanceMetrics m = service.getMetrics(READER, MetricsPeriod.WEEK);

        for (PerformanceMetrics.Metric metric : m.metrics()) {
            long sum = m.series().stream().mapToLong(p -> Long.parseLong(p.values().get(metric.key()))).sum();
            assertThat(Long.toString(sum)).as(metric.key()).isEqualTo(metric.current());
        }
        assertThat(day(m, "2026-10-20", "topupCount")).isEqualTo("0");
        assertThat(day(m, "2026-10-21", "topupCount")).isEqualTo("1");
        assertThat(day(m, "2026-10-21", "transferAmount:KRW")).isEqualTo("500");
    }
}
