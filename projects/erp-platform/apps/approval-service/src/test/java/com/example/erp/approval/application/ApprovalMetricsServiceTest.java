package com.example.erp.approval.application;

import com.example.common.metrics.PerformanceMetrics;
import com.example.common.time.MetricsPeriod;
import com.example.erp.approval.application.port.outbound.AuthorizationPort;
import com.example.erp.approval.application.port.outbound.EmployeeLookup;
import com.example.erp.approval.application.port.outbound.MasterDataPort;
import com.example.erp.approval.domain.authorization.AuthorizationDecision;
import com.example.erp.approval.domain.authorization.RequiredScope;
import com.example.erp.approval.domain.error.ApprovalErrors.PermissionDeniedException;
import com.example.erp.approval.domain.request.ApprovalStatus;
import com.example.erp.approval.domain.request.repository.ApprovalMetricsQuery;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link ApprovalMetricsService} — windows, KST day bucketing, zero-filled series, scope and
 * the READ gate (TASK-MONO-809). Fixed {@link Clock}: Fri 2026-10-23 14:05 KST.
 *
 * <p>Week window: [Mon 10-19 00:00 KST = 10-18T15:00Z, now = 10-23T05:05Z). Previous (same
 * elapsed time): [10-11T15:00Z, 10-16T05:05Z).
 */
@ExtendWith(MockitoExtension.class)
class ApprovalMetricsServiceTest {

    private static final String TENANT = "erp";
    private static final Instant NOW = Instant.parse("2026-10-23T05:05:00Z");
    private static final Instant CUR_FROM = Instant.parse("2026-10-18T15:00:00Z");
    private static final Instant PREV_FROM = Instant.parse("2026-10-11T15:00:00Z");
    private static final Instant PREV_TO = Instant.parse("2026-10-16T05:05:00Z");

    private static final ActorContext OPERATOR = new ActorContext("acct-op", TENANT,
            Set.of("ERP_OPERATOR", "erp.read"), Set.of("dept-1"));
    private static final ActorContext MEMBER = new ActorContext("acct-m", TENANT,
            Set.of("erp.read"), Set.of("dept-1"));
    private static final ActorContext NO_ROLE = new ActorContext("acct-x", TENANT,
            Set.of(), Set.of());

    @Mock ApprovalMetricsQuery metricsQuery;
    @Mock MasterDataPort masterDataPort;
    @Mock AuthorizationPort authorizationPort;

    private ApprovalMetricsService service;
    private TimeZone originalZone;

    @BeforeEach
    void setUp() {
        originalZone = TimeZone.getDefault();
        service = new ApprovalMetricsService(metricsQuery, masterDataPort, authorizationPort,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    private static String value(PerformanceMetrics m, String key, boolean current) {
        return m.metrics().stream().filter(x -> x.key().equals(key)).findFirst()
                .map(x -> current ? x.current() : x.previous()).orElseThrow();
    }

    private static String day(PerformanceMetrics m, String date, String key) {
        return m.series().stream().filter(p -> p.date().equals(date)).findFirst()
                .map(p -> p.values().get(key)).orElseThrow();
    }

    @Test
    @DisplayName("operator: tenant-wide queries; current vs previous window of the same elapsed time")
    void operatorCurrentAndPreviousWindow() {
        lenient().when(metricsQuery.submittedAt(TENANT, CUR_FROM, NOW)).thenReturn(List.of(
                Instant.parse("2026-10-19T01:00:00Z"), Instant.parse("2026-10-20T01:00:00Z"),
                Instant.parse("2026-10-21T01:00:00Z")));
        lenient().when(metricsQuery.submittedAt(TENANT, PREV_FROM, PREV_TO))
                .thenReturn(List.of(Instant.parse("2026-10-12T01:00:00Z")));
        lenient().when(metricsQuery.finalizedAt(TENANT, ApprovalStatus.APPROVED, CUR_FROM, NOW))
                .thenReturn(List.of(Instant.parse("2026-10-22T01:00:00Z"),
                        Instant.parse("2026-10-22T02:00:00Z")));
        lenient().when(metricsQuery.finalizedAt(TENANT, ApprovalStatus.APPROVED, PREV_FROM, PREV_TO))
                .thenReturn(List.of());
        lenient().when(metricsQuery.finalizedAt(TENANT, ApprovalStatus.REJECTED, CUR_FROM, NOW))
                .thenReturn(List.of(Instant.parse("2026-10-23T01:00:00Z")));
        lenient().when(metricsQuery.finalizedAt(TENANT, ApprovalStatus.REJECTED, PREV_FROM, PREV_TO))
                .thenReturn(List.of(Instant.parse("2026-10-13T01:00:00Z"),
                        Instant.parse("2026-10-14T01:00:00Z")));

        PerformanceMetrics m = service.getMetrics(OPERATOR, MetricsPeriod.WEEK);

        assertThat(m.metrics()).extracting(PerformanceMetrics.Metric::key)
                .containsExactly("submitted", "approved", "rejected");
        assertThat(m.metrics()).allSatisfy(x -> {
            assertThat(x.unit()).isEqualTo("count");
            assertThat(x.currency()).isNull();
        });
        assertThat(value(m, "submitted", true)).isEqualTo("3");
        assertThat(value(m, "submitted", false)).isEqualTo("1");
        assertThat(value(m, "approved", true)).isEqualTo("2");
        assertThat(value(m, "approved", false)).isEqualTo("0");
        assertThat(value(m, "rejected", true)).isEqualTo("1");
        assertThat(value(m, "rejected", false)).isEqualTo("2");
        assertThat(m.current().from()).isEqualTo("2026-10-19T00:00:00+09:00");
        assertThat(m.current().to()).isEqualTo("2026-10-23T14:05:00+09:00");
        assertThat(m.previous().from()).isEqualTo("2026-10-12T00:00:00+09:00");
        assertThat(m.previous().to()).isEqualTo("2026-10-16T14:05:00+09:00");
        assertThat(m.timezone()).isEqualTo("Asia/Seoul");
        verifyNoInteractions(masterDataPort);
        verify(metricsQuery, never()).submittedAtByParticipant(anyString(), anyString(), any(), any());
        verify(metricsQuery, never()).finalizedAtByParticipant(anyString(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("series is zero-filled, one row per KST date first→last, keys in metric order")
    void zeroFilledSeries() {
        lenient().when(metricsQuery.submittedAt(TENANT, CUR_FROM, NOW))
                .thenReturn(List.of(Instant.parse("2026-10-21T01:00:00Z")));

        PerformanceMetrics m = service.getMetrics(OPERATOR, MetricsPeriod.WEEK);

        assertThat(m.series()).extracting(PerformanceMetrics.SeriesPoint::date)
                .containsExactly("2026-10-19", "2026-10-20", "2026-10-21", "2026-10-22", "2026-10-23");
        assertThat(day(m, "2026-10-21", "submitted")).isEqualTo("1");
        assertThat(day(m, "2026-10-19", "submitted")).isEqualTo("0");
        assertThat(day(m, "2026-10-21", "approved")).isEqualTo("0");
        assertThat(day(m, "2026-10-23", "rejected")).isEqualTo("0");
        assertThat(m.series().get(0).values().keySet())
                .containsExactly("submitted", "approved", "rejected");
    }

    @ParameterizedTest(name = "JVM default zone {0}")
    @ValueSource(strings = {"UTC", "Asia/Seoul"})
    @DisplayName("KST day bucketing: 15:30Z lands on the NEXT KST date, whatever the JVM zone")
    void kstDayBucketing(String zone) {
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        // 2026-10-20T15:30Z == 2026-10-21 00:30 KST
        lenient().when(metricsQuery.submittedAt(TENANT, CUR_FROM, NOW))
                .thenReturn(List.of(Instant.parse("2026-10-20T15:30:00Z"),
                        // 14:59:59Z is still 10-20 23:59:59 KST
                        Instant.parse("2026-10-20T14:59:59Z")));
        lenient().when(metricsQuery.finalizedAt(TENANT, ApprovalStatus.APPROVED, CUR_FROM, NOW))
                .thenReturn(List.of(Instant.parse("2026-10-22T15:30:00Z")));

        PerformanceMetrics m = service.getMetrics(OPERATOR, MetricsPeriod.WEEK);

        assertThat(day(m, "2026-10-21", "submitted")).isEqualTo("1");
        assertThat(day(m, "2026-10-20", "submitted")).isEqualTo("1");
        assertThat(day(m, "2026-10-23", "approved")).isEqualTo("1");
        assertThat(day(m, "2026-10-22", "approved")).isEqualTo("0");
    }

    @Test
    @DisplayName("platform scope ('*') is tenant-wide like an operator")
    void platformScopeIsTenantWide() {
        ActorContext platform = new ActorContext("acct-p", TENANT, Set.of("erp.read"), Set.of("*"));

        service.getMetrics(platform, MetricsPeriod.WEEK);

        verify(metricsQuery).submittedAt(TENANT, CUR_FROM, NOW);
        verifyNoInteractions(masterDataPort);
    }

    @Test
    @DisplayName("non-operator, linked: participant queries with the EMPLOYEE id, never tenant-wide")
    void linkedMemberUsesParticipantQueries() {
        when(masterDataPort.callerEmployee("acct-m", TENANT))
                .thenReturn(EmployeeLookup.found("emp-7", "ACTIVE", "acct-m"));
        lenient().when(metricsQuery.submittedAtByParticipant(TENANT, "emp-7", CUR_FROM, NOW))
                .thenReturn(List.of(Instant.parse("2026-10-20T01:00:00Z")));

        PerformanceMetrics m = service.getMetrics(MEMBER, MetricsPeriod.WEEK);

        assertThat(value(m, "submitted", true)).isEqualTo("1");
        verify(metricsQuery).submittedAtByParticipant(TENANT, "emp-7", CUR_FROM, NOW);
        verify(metricsQuery).submittedAtByParticipant(TENANT, "emp-7", PREV_FROM, PREV_TO);
        verify(metricsQuery).finalizedAtByParticipant(TENANT, "emp-7", ApprovalStatus.APPROVED, CUR_FROM, NOW);
        verify(metricsQuery).finalizedAtByParticipant(TENANT, "emp-7", ApprovalStatus.REJECTED, CUR_FROM, NOW);
        verify(metricsQuery, never()).submittedAt(anyString(), any(), any());
        verify(metricsQuery, never()).finalizedAt(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("non-operator, unlinked: all zeros, series still full, NO repository call")
    void unlinkedCallerGetsZeros() {
        when(masterDataPort.callerEmployee("acct-m", TENANT))
                .thenReturn(EmployeeLookup.notFound());

        PerformanceMetrics m = service.getMetrics(MEMBER, MetricsPeriod.WEEK);

        assertThat(m.metrics()).allSatisfy(x -> {
            assertThat(x.current()).isEqualTo("0");
            assertThat(x.previous()).isEqualTo("0");
        });
        assertThat(m.series()).hasSize(5);
        verifyNoInteractions(metricsQuery);
    }

    @Test
    @DisplayName("no read permission → PERMISSION_DENIED before any lookup or query")
    void deniedWithoutReadRole() {
        when(authorizationPort.evaluate(NO_ROLE, RequiredScope.READ, null))
                .thenReturn(AuthorizationDecision.denyRole("no read role"));

        assertThatThrownBy(() -> service.getMetrics(NO_ROLE, MetricsPeriod.WEEK))
                .isInstanceOf(PermissionDeniedException.class);

        verifyNoInteractions(metricsQuery, masterDataPort);
    }
}
