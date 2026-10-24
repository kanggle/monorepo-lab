package com.example.security.service.query;

import com.example.security.service.infrastructure.persistence.AccountLockHistoryJpaRepository;
import com.example.security.service.infrastructure.persistence.LoginHistoryJpaRepository;
import com.example.security.service.infrastructure.persistence.LoginOutcomeAt;
import com.example.security.service.query.dto.SecurityMetricsDailyView;
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

import java.time.Instant;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link SecurityMetricsQueryService} (TASK-MONO-811) — the KST day cut, the SUCCESS/FAILURE-only
 * outcome set, the '*' branch and the input guards. The DB side (tenant filter, half-open window,
 * the outcomes really excluded) is {@code SecurityMetricsQueryIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class SecurityMetricsQueryServiceTest {

    private static final String TENANT = "fan-platform";
    private static final List<String> COUNTED = List.of("SUCCESS", "FAILURE");
    // Mon 2026-10-19 00:00 KST → Fri 2026-10-23 14:05 KST
    private static final Instant FROM = Instant.parse("2026-10-18T15:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-23T05:05:00Z");

    @Mock LoginHistoryJpaRepository loginHistory;
    @Mock AccountLockHistoryJpaRepository lockHistory;

    private SecurityMetricsQueryService service;
    private TimeZone originalZone;

    @BeforeEach
    void setUp() {
        originalZone = TimeZone.getDefault();
        service = new SecurityMetricsQueryService(loginHistory, lockHistory);
    }

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    private static LoginOutcomeAt row(String outcome, String at) {
        return new LoginOutcomeAt(outcome, Instant.parse(at));
    }

    private static SecurityMetricsDailyView.Day day(SecurityMetricsDailyView v, String date) {
        return v.days().stream().filter(d -> d.date().equals(date)).findFirst()
                .orElseThrow(() -> new AssertionError("no day " + date + " in " + v.days()));
    }

    @Test
    @DisplayName("asks the DB for SUCCESS and FAILURE only — ATTEMPTED/RATE_LIMITED/TOKEN_REUSE/REFRESH are never read")
    void onlyCountedOutcomesAreQueried() {
        when(loginHistory.findOutcomesInWindow(TENANT, COUNTED, FROM, TO)).thenReturn(List.of());
        when(lockHistory.findOccurredAtInWindow(TENANT, FROM, TO)).thenReturn(List.of());

        SecurityMetricsDailyView v = service.daily(TENANT, FROM, TO);

        assertThat(v.days()).isEmpty();
        assertThat(v.tenantId()).isEqualTo(TENANT);
        assertThat(v.from()).isEqualTo("2026-10-18T15:00:00Z");
        assertThat(v.to()).isEqualTo("2026-10-23T05:05:00Z");
        verify(loginHistory).findOutcomesInWindow(TENANT, COUNTED, FROM, TO);
    }

    @Test
    @DisplayName("🔴 control: one FAILURE row moves only loginFailure; one SUCCESS only loginSuccess; one lock only accountLocked")
    void eachRowMovesOnlyItsKey() {
        when(loginHistory.findOutcomesInWindow(TENANT, COUNTED, FROM, TO)).thenReturn(List.of(
                row("FAILURE", "2026-10-19T01:00:00Z"),
                row("SUCCESS", "2026-10-20T01:00:00Z"),
                row("SUCCESS", "2026-10-20T02:00:00Z"),
                row("SUCCESS", "2026-10-20T03:00:00Z")));
        when(lockHistory.findOccurredAtInWindow(TENANT, FROM, TO))
                .thenReturn(List.of(Instant.parse("2026-10-21T01:00:00Z")));

        SecurityMetricsDailyView v = service.daily(TENANT, FROM, TO);

        assertThat(v.days()).extracting(SecurityMetricsDailyView.Day::date)
                .containsExactly("2026-10-19", "2026-10-20", "2026-10-21");
        assertThat(day(v, "2026-10-19")).isEqualTo(new SecurityMetricsDailyView.Day("2026-10-19", 0, 1, 0));
        // the same person logging in three times = 3 (events, not people)
        assertThat(day(v, "2026-10-20")).isEqualTo(new SecurityMetricsDailyView.Day("2026-10-20", 3, 0, 0));
        assertThat(day(v, "2026-10-21")).isEqualTo(new SecurityMetricsDailyView.Day("2026-10-21", 0, 0, 1));
    }

    @Test
    @DisplayName("an outcome the query should never return (ATTEMPTED) moves nothing even if it leaks through")
    void unexpectedOutcomeMovesNothing() {
        when(loginHistory.findOutcomesInWindow(TENANT, COUNTED, FROM, TO))
                .thenReturn(List.of(row("ATTEMPTED", "2026-10-19T01:00:00Z")));
        when(lockHistory.findOccurredAtInWindow(TENANT, FROM, TO)).thenReturn(List.of());

        SecurityMetricsDailyView v = service.daily(TENANT, FROM, TO);

        assertThat(day(v, "2026-10-19")).isEqualTo(new SecurityMetricsDailyView.Day("2026-10-19", 0, 0, 0));
    }

    @ParameterizedTest(name = "JVM default zone {0}")
    @ValueSource(strings = {"UTC", "Asia/Seoul"})
    @DisplayName("KST day cut: 15:00Z is the NEXT KST date, 14:59:59.999Z the same one — whatever the JVM zone")
    void kstMidnight(String zone) {
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        when(loginHistory.findOutcomesInWindow(TENANT, COUNTED, FROM, TO)).thenReturn(List.of(
                row("SUCCESS", "2026-10-20T14:59:59.999Z"),   // 10-20 23:59:59.999 KST
                row("SUCCESS", "2026-10-20T15:00:00Z")));     // 10-21 00:00 KST
        when(lockHistory.findOccurredAtInWindow(TENANT, FROM, TO))
                .thenReturn(List.of(Instant.parse("2026-10-21T15:30:00Z")));  // 10-22 00:30 KST

        SecurityMetricsDailyView v = service.daily(TENANT, FROM, TO);

        assertThat(day(v, "2026-10-20").loginSuccess()).isEqualTo(1);
        assertThat(day(v, "2026-10-21").loginSuccess()).isEqualTo(1);
        assertThat(day(v, "2026-10-22").accountLocked()).isEqualTo(1);
        assertThat(v.days()).noneMatch(d -> d.date().equals("2026-10-23"));
    }

    @Test
    @DisplayName("'*' (platform scope) → the all-tenant queries, never a tenant-filtered one")
    void allTenants() {
        when(loginHistory.findOutcomesInWindowAllTenants(COUNTED, FROM, TO))
                .thenReturn(List.of(row("SUCCESS", "2026-10-19T01:00:00Z")));
        when(lockHistory.findOccurredAtInWindowAllTenants(FROM, TO)).thenReturn(List.of());

        SecurityMetricsDailyView v = service.daily("*", FROM, TO);

        assertThat(v.tenantId()).isEqualTo("*");
        assertThat(day(v, "2026-10-19").loginSuccess()).isEqualTo(1);
    }

    @Test
    @DisplayName("guards: blank tenant, to < from, window > 62 days → IllegalArgumentException (400), no query")
    void guards() {
        assertThatThrownBy(() -> service.daily(" ", FROM, TO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.daily(TENANT, TO, FROM)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.daily(TENANT, FROM, FROM.plusSeconds(63L * 86400)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(loginHistory, lockHistory);
    }

    @Test
    @DisplayName("an empty window (from == to) is valid and answers no days")
    void emptyWindow() {
        when(loginHistory.findOutcomesInWindow(any(), any(), any(), any())).thenReturn(List.of());
        when(lockHistory.findOccurredAtInWindow(any(), any(), any())).thenReturn(List.of());

        assertThat(service.daily(TENANT, FROM, FROM).days()).isEmpty();
    }
}
