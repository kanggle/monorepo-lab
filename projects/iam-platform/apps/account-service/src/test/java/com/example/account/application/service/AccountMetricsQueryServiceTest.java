package com.example.account.application.service;

import com.example.account.application.port.AccountMetricsQueryPort;
import com.example.account.application.result.AccountMetricsDailyResult;
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
import java.time.LocalDate;
import java.util.List;
import java.util.TimeZone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@link AccountMetricsQueryService} (TASK-MONO-811) — which sources each tenant kind adds up, the
 * KST day cut and the guards. The SQL side (tenant filter, half-open window, statuses) is
 * {@code AccountMetricsQueryIntegrationTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class AccountMetricsQueryServiceTest {

    private static final Instant FROM = Instant.parse("2026-09-30T15:00:00Z");  // Oct 1 00:00 KST
    private static final Instant TO = Instant.parse("2026-10-23T05:05:00Z");

    @Mock AccountMetricsQueryPort port;

    private AccountMetricsQueryService service;
    private TimeZone originalZone;

    @BeforeEach
    void setUp() {
        originalZone = TimeZone.getDefault();
        service = new AccountMetricsQueryService(port);
    }

    @AfterEach
    void restoreZone() {
        TimeZone.setDefault(originalZone);
    }

    private static long on(AccountMetricsDailyResult r, String date) {
        return r.days().stream().filter(d -> d.date().equals(LocalDate.parse(date)))
                .mapToLong(AccountMetricsDailyResult.Day::newAccount).sum();
    }

    @Test
    @DisplayName("🔴 site tenant: its own accounts PLUS pool members who joined it in the window")
    void siteAddsMemberships() {
        when(port.accountsCreatedIn("fan-platform", FROM, TO))
                .thenReturn(List.of(Instant.parse("2026-10-02T01:00:00Z")));
        when(port.siteMembershipsConsentedIn("fan-platform", FROM, TO))
                .thenReturn(List.of(Instant.parse("2026-10-02T02:00:00Z"), Instant.parse("2026-10-05T02:00:00Z")));

        AccountMetricsDailyResult r = service.daily("fan-platform", FROM, TO);

        assertThat(on(r, "2026-10-02")).isEqualTo(2);
        assertThat(on(r, "2026-10-05")).isEqualTo(1);
        assertThat(r.days()).extracting(AccountMetricsDailyResult.Day::date)
                .containsExactly(LocalDate.parse("2026-10-02"), LocalDate.parse("2026-10-05"));
    }

    @Test
    @DisplayName("consumer-pool: accounts rows only — memberships are never read")
    void poolAccountsOnly() {
        when(port.accountsCreatedIn("consumer-pool", FROM, TO))
                .thenReturn(List.of(Instant.parse("2026-10-02T01:00:00Z")));

        AccountMetricsDailyResult r = service.daily("consumer-pool", FROM, TO);

        assertThat(on(r, "2026-10-02")).isEqualTo(1);
        verify(port, never()).siteMembershipsConsentedIn(any(), any(), any());
    }

    @Test
    @DisplayName("🔴 '*': every tenant's accounts rows only — memberships NOT added (a pool account counts once)")
    void allTenantsNoDoubleCount() {
        when(port.accountsCreatedInAllTenants(FROM, TO))
                .thenReturn(List.of(Instant.parse("2026-10-02T01:00:00Z"), Instant.parse("2026-10-02T03:00:00Z")));

        AccountMetricsDailyResult r = service.daily("*", FROM, TO);

        assertThat(on(r, "2026-10-02")).isEqualTo(2);
        verify(port, never()).siteMembershipsConsentedIn(any(), any(), any());
        verify(port, never()).accountsCreatedIn(any(), any(), any());
    }

    @ParameterizedTest(name = "JVM default zone {0}")
    @ValueSource(strings = {"UTC", "Asia/Seoul"})
    @DisplayName("KST day cut across midnight, whatever the JVM zone")
    void kstMidnight(String zone) {
        TimeZone.setDefault(TimeZone.getTimeZone(zone));
        when(port.accountsCreatedIn("ecommerce", FROM, TO)).thenReturn(List.of(
                Instant.parse("2026-10-09T14:59:59Z"),     // Oct 9 23:59:59 KST
                Instant.parse("2026-10-09T15:00:00Z")));   // Oct 10 00:00 KST
        when(port.siteMembershipsConsentedIn("ecommerce", FROM, TO)).thenReturn(List.of());

        AccountMetricsDailyResult r = service.daily("ecommerce", FROM, TO);

        assertThat(on(r, "2026-10-09")).isEqualTo(1);
        assertThat(on(r, "2026-10-10")).isEqualTo(1);
    }

    @Test
    @DisplayName("guards: blank tenant, to < from, window > 62 days → IllegalArgumentException (400)")
    void guards() {
        assertThatThrownBy(() -> service.daily("", FROM, TO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.daily("fan-platform", TO, FROM)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.daily("fan-platform", FROM, FROM.plusSeconds(63L * 86400)))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(port);
    }
}
