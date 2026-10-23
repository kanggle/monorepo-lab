package com.example.scmplatform.procurement.application;

import com.example.common.metrics.PerformanceMetrics;
import com.example.common.time.MetricsPeriod;
import com.example.scmplatform.procurement.domain.po.repository.PurchaseOrderMetricsQuery;
import com.example.scmplatform.procurement.domain.po.repository.ReceivedTransitionRow;
import com.example.scmplatform.procurement.domain.po.repository.SubmittedPoRow;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link PurchaseOrderMetricsService} (TASK-MONO-810) — windows, the CANCELED exclusion,
 * per-currency keys and minor-unit rounding, distinct received POs, KST day bucketing and the
 * zero-filled series. Fixed {@link Clock}: Fri 2026-10-23 14:05 KST.
 *
 * <p>Week window: [Mon 10-19 00:00 KST = 10-18T15:00Z, now = 10-23T05:05Z). Previous (same
 * elapsed time): [10-11T15:00Z, 10-16T05:05Z).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class PurchaseOrderMetricsServiceTest {

    private static final String TENANT = "scm";
    private static final Instant NOW = Instant.parse("2026-10-23T05:05:00Z");
    private static final Instant CUR_FROM = Instant.parse("2026-10-18T15:00:00Z");
    private static final Instant PREV_FROM = Instant.parse("2026-10-11T15:00:00Z");
    private static final Instant PREV_TO = Instant.parse("2026-10-16T05:05:00Z");

    private static final ActorContext READER = new ActorContext("acct-r", TENANT, Set.of("SCM_PROCUREMENT_READ"));

    @Mock PurchaseOrderMetricsQuery metricsQuery;

    private PurchaseOrderMetricsService service;
    private TimeZone originalZone;

    @BeforeEach
    void setUp() {
        originalZone = TimeZone.getDefault();
        service = new PurchaseOrderMetricsService(metricsQuery, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    // ---- helpers -----------------------------------------------------------

    private void stubWeek(List<SubmittedPoRow> curSubmitted, List<SubmittedPoRow> prevSubmitted,
                          List<ReceivedTransitionRow> curReceived, List<ReceivedTransitionRow> prevReceived) {
        when(metricsQuery.submittedIn(TENANT, CUR_FROM, NOW)).thenReturn(curSubmitted);
        when(metricsQuery.submittedIn(TENANT, PREV_FROM, PREV_TO)).thenReturn(prevSubmitted);
        when(metricsQuery.receivedIn(TENANT, CUR_FROM, NOW)).thenReturn(curReceived);
        when(metricsQuery.receivedIn(TENANT, PREV_FROM, PREV_TO)).thenReturn(prevReceived);
    }

    private static SubmittedPoRow po(String at, PoStatus status, String amount, String currency) {
        return new SubmittedPoRow(Instant.parse(at), status, new BigDecimal(amount), currency);
    }

    private static ReceivedTransitionRow received(String poId, String at) {
        return new ReceivedTransitionRow(poId, Instant.parse(at));
    }

    private static PerformanceMetrics.Metric metric(PerformanceMetrics m, String key) {
        return m.metrics().stream().filter(x -> x.key().equals(key)).findFirst()
                .orElseThrow(() -> new AssertionError("no metric " + key + " in " + m.metrics()));
    }

    private static String day(PerformanceMetrics m, String date, String key) {
        return m.series().stream().filter(p -> p.date().equals(date)).findFirst()
                .map(p -> p.values().get(key)).orElseThrow();
    }

    // ---- windows -----------------------------------------------------------

    @Test
    @DisplayName("week: current [Mon 00:00 KST, now) vs previous of the same elapsed time; values per window")
    void currentAndPreviousWindow() {
        stubWeek(
                List.of(po("2026-10-19T01:00:00Z", PoStatus.SUBMITTED, "100000", "KRW"),
                        po("2026-10-21T01:00:00Z", PoStatus.CONFIRMED, "50000", "KRW")),
                List.of(po("2026-10-12T01:00:00Z", PoStatus.ACKNOWLEDGED, "70000", "KRW")),
                List.of(received("po-1", "2026-10-22T01:00:00Z")),
                List.of(received("po-2", "2026-10-13T01:00:00Z"), received("po-3", "2026-10-14T01:00:00Z")));

        PerformanceMetrics m = service.getMetrics(READER, MetricsPeriod.WEEK);

        assertThat(m.metrics()).extracting(PerformanceMetrics.Metric::key)
                .containsExactly("poCount", "poAmount:KRW", "receivedCount");
        assertThat(metric(m, "poCount").current()).isEqualTo("2");
        assertThat(metric(m, "poCount").previous()).isEqualTo("1");
        assertThat(metric(m, "poCount").unit()).isEqualTo("count");
        assertThat(metric(m, "poCount").currency()).isNull();
        assertThat(metric(m, "poAmount:KRW").current()).isEqualTo("150000");
        assertThat(metric(m, "poAmount:KRW").previous()).isEqualTo("70000");
        assertThat(metric(m, "poAmount:KRW").unit()).isEqualTo("money");
        assertThat(metric(m, "poAmount:KRW").currency()).isEqualTo("KRW");
        assertThat(metric(m, "receivedCount").current()).isEqualTo("1");
        assertThat(metric(m, "receivedCount").previous()).isEqualTo("2");
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
        // 2026-03-30 12:00 KST
        Instant now = Instant.parse("2026-03-30T03:00:00Z");
        Instant curFrom = Instant.parse("2026-02-28T15:00:00Z");   // Mar 1 00:00 KST
        Instant prevFrom = Instant.parse("2026-01-31T15:00:00Z");  // Feb 1 00:00 KST
        service = new PurchaseOrderMetricsService(metricsQuery, Clock.fixed(now, ZoneOffset.UTC));
        when(metricsQuery.submittedIn(TENANT, curFrom, now)).thenReturn(List.of());
        when(metricsQuery.submittedIn(TENANT, prevFrom, curFrom)).thenReturn(List.of());
        when(metricsQuery.receivedIn(TENANT, curFrom, now)).thenReturn(List.of());
        when(metricsQuery.receivedIn(TENANT, prevFrom, curFrom)).thenReturn(List.of());

        PerformanceMetrics m = service.getMetrics(READER, MetricsPeriod.MONTH);

        assertThat(m.previous().from()).isEqualTo("2026-02-01T00:00:00+09:00");
        assertThat(m.previous().to()).isEqualTo("2026-03-01T00:00:00+09:00");
        assertThat(m.series()).hasSize(30);
        verify(metricsQuery).submittedIn(TENANT, prevFrom, curFrom);
    }

    // ---- AC-1 control group --------------------------------------------------

    @Test
    @DisplayName("🔴 control: a PO currently CANCELED moves neither poCount nor poAmount; every other submitted status does")
    void canceledExcluded() {
        stubWeek(
                List.of(po("2026-10-19T01:00:00Z", PoStatus.SUBMITTED, "100", "KRW"),
                        po("2026-10-19T02:00:00Z", PoStatus.ACKNOWLEDGED, "200", "KRW"),
                        po("2026-10-19T03:00:00Z", PoStatus.CONFIRMED, "400", "KRW"),
                        po("2026-10-19T04:00:00Z", PoStatus.RECEIVED, "800", "KRW"),
                        po("2026-10-19T05:00:00Z", PoStatus.CANCELED, "1600", "KRW")),
                List.of(po("2026-10-12T01:00:00Z", PoStatus.CANCELED, "3200", "KRW")),
                List.of(), List.of());

        PerformanceMetrics m = service.getMetrics(READER, MetricsPeriod.WEEK);

        assertThat(metric(m, "poCount").current()).isEqualTo("4");
        assertThat(metric(m, "poAmount:KRW").current()).isEqualTo("1500");
        assertThat(metric(m, "poCount").previous()).isEqualTo("0");
        assertThat(metric(m, "poAmount:KRW").previous()).isEqualTo("0");
        assertThat(day(m, "2026-10-19", "poCount")).isEqualTo("4");
        assertThat(day(m, "2026-10-19", "poAmount:KRW")).isEqualTo("1500");
    }

    @Test
    @DisplayName("a currency present only through a CANCELED PO gets no key (it is not in the poCount set)")
    void canceledOnlyCurrencyHasNoKey() {
        stubWeek(
                List.of(po("2026-10-19T01:00:00Z", PoStatus.CANCELED, "10.00", "USD")),
                List.of(), List.of(), List.of());

        PerformanceMetrics m = service.getMetrics(READER, MetricsPeriod.WEEK);

        assertThat(m.metrics()).extracting(PerformanceMetrics.Metric::key)
                .containsExactly("poCount", "poAmount:KRW", "receivedCount");
        assertThat(metric(m, "poAmount:KRW").current()).isEqualTo("0");
    }

    // ---- currencies & rounding ----------------------------------------------

    @Test
    @DisplayName("multi-currency: one key per currency, sorted, never added together; a previous-only currency keeps its key")
    void multiCurrencyKeys() {
        stubWeek(
                List.of(po("2026-10-19T01:00:00Z", PoStatus.SUBMITTED, "100000", "KRW"),
                        po("2026-10-20T01:00:00Z", PoStatus.SUBMITTED, "12.345", "USD")),
                List.of(po("2026-10-12T01:00:00Z", PoStatus.SUBMITTED, "5.00", "EUR")),
                List.of(), List.of());

        PerformanceMetrics m = service.getMetrics(READER, MetricsPeriod.WEEK);

        assertThat(m.metrics()).extracting(PerformanceMetrics.Metric::key)
                .containsExactly("poCount", "poAmount:EUR", "poAmount:KRW", "poAmount:USD", "receivedCount");
        assertThat(metric(m, "poAmount:KRW").current()).isEqualTo("100000");
        // USD 12.345 → 1234.5 cents → HALF_UP → 1235
        assertThat(metric(m, "poAmount:USD").current()).isEqualTo("1235");
        assertThat(metric(m, "poAmount:USD").currency()).isEqualTo("USD");
        assertThat(metric(m, "poAmount:EUR").current()).isEqualTo("0");
        assertThat(metric(m, "poAmount:EUR").previous()).isEqualTo("500");
        assertThat(metric(m, "poCount").current()).isEqualTo("2");
        assertThat(day(m, "2026-10-20", "poAmount:USD")).isEqualTo("1235");
        assertThat(day(m, "2026-10-20", "poAmount:KRW")).isEqualTo("0");
        assertThat(day(m, "2026-10-19", "poAmount:EUR")).isEqualTo("0");
        assertThat(m.series().get(0).values().keySet())
                .containsExactly("poCount", "poAmount:EUR", "poAmount:KRW", "poAmount:USD", "receivedCount");
    }

    @Test
    @DisplayName("rounding happens once on the per-currency sum: KRW 0.25 + 0.25 = 0.50 → 1 (per-PO rounding would give 0)")
    void roundOnceOnTheSum() {
        stubWeek(
                List.of(po("2026-10-19T01:00:00Z", PoStatus.SUBMITTED, "0.25", "KRW"),
                        po("2026-10-19T02:00:00Z", PoStatus.SUBMITTED, "0.25", "KRW")),
                List.of(), List.of(), List.of());

        PerformanceMetrics m = service.getMetrics(READER, MetricsPeriod.WEEK);

        assertThat(metric(m, "poAmount:KRW").current()).isEqualTo("1");
        assertThat(day(m, "2026-10-19", "poAmount:KRW")).isEqualTo("1");
    }

    @Test
    @DisplayName("no submitted PO in either window → a single poAmount:KRW \"0\"/\"0\"")
    void noPosDefaultKrwKey() {
        stubWeek(List.of(), List.of(), List.of(), List.of());

        PerformanceMetrics m = service.getMetrics(READER, MetricsPeriod.WEEK);

        assertThat(m.metrics()).extracting(PerformanceMetrics.Metric::key)
                .containsExactly("poCount", "poAmount:KRW", "receivedCount");
        assertThat(metric(m, "poAmount:KRW").current()).isEqualTo("0");
        assertThat(metric(m, "poAmount:KRW").previous()).isEqualTo("0");
        assertThat(metric(m, "poAmount:KRW").currency()).isEqualTo("KRW");
        assertThat(m.metrics()).allSatisfy(x -> {
            assertThat(x.current()).isEqualTo("0");
            assertThat(x.previous()).isEqualTo("0");
        });
    }

    @Test
    @DisplayName("minor units: ISO default fraction digits (KRW 0, USD 2, JPY 0, BHD 3); non-ISO and XXX → 2; HALF_UP")
    void minorUnits() {
        assertThat(PurchaseOrderMetricsService.toMinorUnits(new BigDecimal("1234.50"), "KRW")).isEqualTo(1235L);
        assertThat(PurchaseOrderMetricsService.toMinorUnits(new BigDecimal("1234.49"), "KRW")).isEqualTo(1234L);
        assertThat(PurchaseOrderMetricsService.toMinorUnits(new BigDecimal("12.34"), "USD")).isEqualTo(1234L);
        assertThat(PurchaseOrderMetricsService.toMinorUnits(new BigDecimal("12.345"), "USD")).isEqualTo(1235L);
        assertThat(PurchaseOrderMetricsService.toMinorUnits(new BigDecimal("12.344"), "USD")).isEqualTo(1234L);
        assertThat(PurchaseOrderMetricsService.toMinorUnits(new BigDecimal("100.5"), "JPY")).isEqualTo(101L);
        assertThat(PurchaseOrderMetricsService.toMinorUnits(new BigDecimal("1.25"), "BHD")).isEqualTo(1250L);
        assertThat(PurchaseOrderMetricsService.fractionDigits("ZZQ")).isEqualTo(2);
        assertThat(PurchaseOrderMetricsService.fractionDigits("XXX")).isEqualTo(2);
        assertThat(PurchaseOrderMetricsService.toMinorUnits(new BigDecimal("7.005"), "ZZQ")).isEqualTo(701L);
    }

    // ---- receivedCount ----------------------------------------------------

    @Test
    @DisplayName("receivedCount counts distinct POs; a PO with two RECEIVED transitions counts once, on its earliest day")
    void receivedDistinctPos() {
        stubWeek(List.of(), List.of(),
                List.of(received("po-1", "2026-10-21T01:00:00Z"),
                        received("po-1", "2026-10-19T01:00:00Z"),
                        received("po-2", "2026-10-21T02:00:00Z")),
                List.of());

        PerformanceMetrics m = service.getMetrics(READER, MetricsPeriod.WEEK);

        assertThat(metric(m, "receivedCount").current()).isEqualTo("2");
        assertThat(day(m, "2026-10-19", "receivedCount")).isEqualTo("1");
        assertThat(day(m, "2026-10-21", "receivedCount")).isEqualTo("1");
    }

    // ---- series -----------------------------------------------------------

    @Test
    @DisplayName("series is zero-filled, one row per KST date first→last, every key on every row in metric order")
    void zeroFilledSeries() {
        stubWeek(List.of(po("2026-10-21T01:00:00Z", PoStatus.SUBMITTED, "300", "KRW")),
                List.of(), List.of(), List.of());

        PerformanceMetrics m = service.getMetrics(READER, MetricsPeriod.WEEK);

        assertThat(m.series()).extracting(PerformanceMetrics.SeriesPoint::date)
                .containsExactly("2026-10-19", "2026-10-20", "2026-10-21", "2026-10-22", "2026-10-23");
        assertThat(m.series()).allSatisfy(p -> assertThat(p.values().keySet())
                .containsExactly("poCount", "poAmount:KRW", "receivedCount"));
        assertThat(day(m, "2026-10-21", "poCount")).isEqualTo("1");
        assertThat(day(m, "2026-10-21", "poAmount:KRW")).isEqualTo("300");
        assertThat(day(m, "2026-10-19", "poCount")).isEqualTo("0");
        assertThat(day(m, "2026-10-19", "poAmount:KRW")).isEqualTo("0");
        assertThat(day(m, "2026-10-23", "receivedCount")).isEqualTo("0");
    }

    @ParameterizedTest(name = "JVM default zone {0}")
    @ValueSource(strings = {"UTC", "Asia/Seoul"})
    @DisplayName("KST day bucketing: 15:30Z lands on the NEXT KST date, 14:59:59Z on the same one, whatever the JVM zone")
    void kstDayBucketing(String zone) {
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        stubWeek(
                // 2026-10-20T15:30Z == 2026-10-21 00:30 KST; 14:59:59Z == 10-20 23:59:59 KST
                List.of(po("2026-10-20T15:30:00Z", PoStatus.SUBMITTED, "1000", "KRW"),
                        po("2026-10-20T14:59:59Z", PoStatus.SUBMITTED, "20", "KRW")),
                List.of(),
                List.of(received("po-9", "2026-10-22T15:30:00Z")),
                List.of());

        PerformanceMetrics m = service.getMetrics(READER, MetricsPeriod.WEEK);

        assertThat(day(m, "2026-10-21", "poCount")).isEqualTo("1");
        assertThat(day(m, "2026-10-21", "poAmount:KRW")).isEqualTo("1000");
        assertThat(day(m, "2026-10-20", "poCount")).isEqualTo("1");
        assertThat(day(m, "2026-10-20", "poAmount:KRW")).isEqualTo("20");
        assertThat(day(m, "2026-10-23", "receivedCount")).isEqualTo("1");
        assertThat(day(m, "2026-10-22", "receivedCount")).isEqualTo("0");
    }
}
