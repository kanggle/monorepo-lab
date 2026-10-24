package com.example.admin.application;

import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.infrastructure.client.AccountServiceClient;
import com.example.admin.infrastructure.client.AccountServiceClient.AccountMetricsDaily;
import com.example.admin.infrastructure.client.SecurityServiceClient;
import com.example.admin.infrastructure.client.SecurityServiceClient.SecurityMetricsDaily;
import com.example.common.metrics.PerformanceMetrics;
import com.example.common.time.MetricsPeriod;
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

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AdminMetricsUseCase} (TASK-MONO-811) — the gate call, the exact window bounds sent downstream
 * (injected {@link Clock}), totals vs. series, key order, zero-fill, and «no partial answer».
 *
 * <p>Week fixture: Fri 2026-10-23 14:05 KST. Current [Mon 10-19 00:00 KST = 10-18T15:00Z, now = 10-23T05:05Z);
 * previous [10-11T15:00Z, 10-16T05:05Z).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AdminMetricsUseCaseTest {

    private static final OperatorContext OP = new OperatorContext("op-1", "jti-1");
    private static final String T = "fan-platform";

    private static final Instant NOW = Instant.parse("2026-10-23T05:05:00Z");
    private static final Instant CUR_FROM = Instant.parse("2026-10-18T15:00:00Z");
    private static final Instant PREV_FROM = Instant.parse("2026-10-11T15:00:00Z");
    private static final Instant PREV_TO = Instant.parse("2026-10-16T05:05:00Z");

    @Mock QueryTenantScopeGate gate;
    @Mock SecurityServiceClient security;
    @Mock AccountServiceClient accounts;

    private TimeZone originalZone;

    @BeforeEach
    void saveZone() {
        originalZone = TimeZone.getDefault();
    }

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    private AdminMetricsUseCase useCase(Instant now) {
        return new AdminMetricsUseCase(gate, security, accounts, Clock.fixed(now, ZoneOffset.UTC));
    }

    private void gateResolves(String requested, String resolved, boolean platform) {
        when(gate.resolve(OP, requested, ActionCode.METRICS_QUERY, "audit.read"))
                .thenReturn(new QueryTenantScopeGate.Resolved(resolved, platform));
    }

    private static SecurityMetricsDaily sec(SecurityMetricsDaily.Day... days) {
        return new SecurityMetricsDaily(T, "f", "t", List.of(days));
    }

    private static SecurityMetricsDaily.Day sd(String date, long ok, long fail, long locked) {
        return new SecurityMetricsDaily.Day(date, ok, fail, locked);
    }

    private static AccountMetricsDaily acc(AccountMetricsDaily.Day... days) {
        return new AccountMetricsDaily(T, "f", "t", List.of(days));
    }

    private static AccountMetricsDaily.Day ad(String date, long n) {
        return new AccountMetricsDaily.Day(date, n);
    }

    private static PerformanceMetrics.Metric metric(PerformanceMetrics m, String key) {
        return m.metrics().stream().filter(x -> x.key().equals(key)).findFirst().orElseThrow();
    }

    private static String day(PerformanceMetrics m, String date, String key) {
        return m.series().stream().filter(p -> p.date().equals(date)).findFirst()
                .map(p -> p.values().get(key)).orElseThrow();
    }

    private void stubWeek(String tenant, SecurityMetricsDaily secNow, SecurityMetricsDaily secPrev,
                          AccountMetricsDaily accNow, AccountMetricsDaily accPrev) {
        when(security.loginMetricsDaily(tenant, CUR_FROM, NOW)).thenReturn(secNow);
        when(security.loginMetricsDaily(tenant, PREV_FROM, PREV_TO)).thenReturn(secPrev);
        when(accounts.newAccountMetricsDaily(tenant, CUR_FROM, NOW)).thenReturn(accNow);
        when(accounts.newAccountMetricsDaily(tenant, PREV_FROM, PREV_TO)).thenReturn(accPrev);
    }

    @Test
    @DisplayName("week: four keys in display order, count/null currency, totals = Σ days per window, series = current days")
    void weekTotalsAndSeries() {
        gateResolves(null, T, false);
        stubWeek(T,
                sec(sd("2026-10-19", 3, 1, 0), sd("2026-10-21", 2, 0, 1)),
                sec(sd("2026-10-12", 7, 4, 2)),
                acc(ad("2026-10-20", 5)),
                acc(ad("2026-10-13", 1), ad("2026-10-14", 1)));

        PerformanceMetrics m = useCase(NOW).query(OP, MetricsPeriod.WEEK, null);

        assertThat(m.metrics()).extracting(PerformanceMetrics.Metric::key)
                .containsExactly("loginSuccess", "loginFailure", "accountLocked", "newAccount");
        assertThat(m.metrics()).allSatisfy(x -> {
            assertThat(x.unit()).isEqualTo("count");
            assertThat(x.currency()).isNull();
        });
        assertThat(metric(m, "loginSuccess").current()).isEqualTo("5");
        assertThat(metric(m, "loginSuccess").previous()).isEqualTo("7");
        assertThat(metric(m, "loginFailure").current()).isEqualTo("1");
        assertThat(metric(m, "loginFailure").previous()).isEqualTo("4");
        assertThat(metric(m, "accountLocked").current()).isEqualTo("1");
        assertThat(metric(m, "accountLocked").previous()).isEqualTo("2");
        assertThat(metric(m, "newAccount").current()).isEqualTo("5");
        assertThat(metric(m, "newAccount").previous()).isEqualTo("2");

        assertThat(m.series()).extracting(PerformanceMetrics.SeriesPoint::date)
                .containsExactly("2026-10-19", "2026-10-20", "2026-10-21", "2026-10-22", "2026-10-23");
        assertThat(day(m, "2026-10-19", "loginSuccess")).isEqualTo("3");
        assertThat(day(m, "2026-10-19", "loginFailure")).isEqualTo("1");
        assertThat(day(m, "2026-10-20", "newAccount")).isEqualTo("5");
        assertThat(day(m, "2026-10-20", "loginSuccess")).isEqualTo("0");
        assertThat(day(m, "2026-10-21", "accountLocked")).isEqualTo("1");
        assertThat(day(m, "2026-10-23", "newAccount")).isEqualTo("0");
        assertThat(m.current().from()).isEqualTo("2026-10-19T00:00:00+09:00");
        assertThat(m.current().to()).isEqualTo("2026-10-23T14:05:00+09:00");
        assertThat(m.previous().from()).isEqualTo("2026-10-12T00:00:00+09:00");
        assertThat(m.previous().to()).isEqualTo("2026-10-16T14:05:00+09:00");
        assertThat(m.period()).isEqualTo("week");
        assertThat(m.timezone()).isEqualTo("Asia/Seoul");
    }

    @Test
    @DisplayName("the gate's resolved tenant ('*' for a platform operator) is what both downstreams receive")
    void resolvedTenantIsForwarded() {
        gateResolves("*", "*", true);
        stubWeek("*", sec(), sec(), acc(), acc());

        PerformanceMetrics m = useCase(NOW).query(OP, MetricsPeriod.WEEK, "*");

        assertThat(m.metrics()).allSatisfy(x -> {
            assertThat(x.current()).isEqualTo("0");
            assertThat(x.previous()).isEqualTo("0");
        });
        assertThat(m.series()).hasSize(5);
    }

    @ParameterizedTest(name = "JVM default zone {0}")
    @ValueSource(strings = {"UTC", "Asia/Seoul"})
    @DisplayName("across KST midnight: at Mon 00:00:30 KST (Sun 15:00:30Z) the week is that one Monday — the windows sent are KST")
    void acrossKstMidnight(String zone) {
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        Instant now = Instant.parse("2026-10-18T15:00:30Z");          // Mon 2026-10-19 00:00:30 KST
        Instant curFrom = Instant.parse("2026-10-18T15:00:00Z");      // Mon 00:00 KST
        Instant prevFrom = Instant.parse("2026-10-11T15:00:00Z");
        Instant prevTo = Instant.parse("2026-10-11T15:00:30Z");
        gateResolves(T, T, false);
        when(security.loginMetricsDaily(T, curFrom, now)).thenReturn(sec(sd("2026-10-19", 1, 0, 0)));
        when(security.loginMetricsDaily(T, prevFrom, prevTo)).thenReturn(sec());
        when(accounts.newAccountMetricsDaily(T, curFrom, now)).thenReturn(acc());
        when(accounts.newAccountMetricsDaily(T, prevFrom, prevTo)).thenReturn(acc());

        PerformanceMetrics m = useCase(now).query(OP, MetricsPeriod.WEEK, T);

        assertThat(m.series()).extracting(PerformanceMetrics.SeriesPoint::date).containsExactly("2026-10-19");
        assertThat(day(m, "2026-10-19", "loginSuccess")).isEqualTo("1");
    }

    @Test
    @DisplayName("month on Mar 30 (short February): previous = Feb 1 → Mar 1, sent with those exact bounds")
    void monthAcrossShortMonth() {
        Instant now = Instant.parse("2026-03-30T03:00:00Z");       // Mar 30 12:00 KST
        Instant curFrom = Instant.parse("2026-02-28T15:00:00Z");   // Mar 1 00:00 KST
        Instant prevFrom = Instant.parse("2026-01-31T15:00:00Z");  // Feb 1 00:00 KST
        gateResolves(T, T, false);
        when(security.loginMetricsDaily(T, curFrom, now)).thenReturn(sec());
        when(security.loginMetricsDaily(T, prevFrom, curFrom)).thenReturn(sec(sd("2026-02-27", 9, 0, 0)));
        when(accounts.newAccountMetricsDaily(T, curFrom, now)).thenReturn(acc());
        when(accounts.newAccountMetricsDaily(T, prevFrom, curFrom)).thenReturn(acc());

        PerformanceMetrics m = useCase(now).query(OP, MetricsPeriod.MONTH, T);

        assertThat(m.previous().from()).isEqualTo("2026-02-01T00:00:00+09:00");
        assertThat(m.previous().to()).isEqualTo("2026-03-01T00:00:00+09:00");
        assertThat(m.series()).hasSize(30);
        assertThat(metric(m, "loginSuccess").previous()).isEqualTo("9");
    }

    @Test
    @DisplayName("🔴 no partial answer: account-service failing after security answered → the whole call fails")
    void downstreamFailureFailsTheWholeCall() {
        gateResolves(T, T, false);
        when(security.loginMetricsDaily(any(), any(), any())).thenReturn(sec());
        when(accounts.newAccountMetricsDaily(T, CUR_FROM, NOW))
                .thenThrow(new DownstreamFailureException("account-service unavailable"));

        assertThatThrownBy(() -> useCase(NOW).query(OP, MetricsPeriod.WEEK, T))
                .isInstanceOf(DownstreamFailureException.class);
    }

    @Test
    @DisplayName("a downstream date outside the current window → 503 (DownstreamFailure), never a 400 or a silent drop")
    void dateOutsideWindowIsADownstreamFault() {
        gateResolves(T, T, false);
        stubWeek(T, sec(sd("2026-10-24", 1, 0, 0)), sec(), acc(), acc());

        assertThatThrownBy(() -> useCase(NOW).query(OP, MetricsPeriod.WEEK, T))
                .isInstanceOf(DownstreamFailureException.class)
                .hasMessageContaining("outside the current window");
    }

    @Test
    @DisplayName("an unparseable downstream date → 503 (DownstreamFailure)")
    void badDateIsADownstreamFault() {
        gateResolves(T, T, false);
        stubWeek(T, sec(), sec(), acc(ad("20/10/2026", 1)), acc());

        assertThatThrownBy(() -> useCase(NOW).query(OP, MetricsPeriod.WEEK, T))
                .isInstanceOf(DownstreamFailureException.class);
    }

    @Test
    @DisplayName("tenant out of the audit.read grant scope → TenantScopeDeniedException, no downstream call")
    void scopeDenied() {
        when(gate.resolve(OP, "other", ActionCode.METRICS_QUERY, "audit.read"))
                .thenThrow(new TenantScopeDeniedException("nope"));

        assertThatThrownBy(() -> useCase(NOW).query(OP, MetricsPeriod.WEEK, "other"))
                .isInstanceOf(TenantScopeDeniedException.class);
        verifyNoInteractions(security, accounts);
    }
}
