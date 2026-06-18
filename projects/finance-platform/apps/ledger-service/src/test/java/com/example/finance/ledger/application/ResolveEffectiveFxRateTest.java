package com.example.finance.ledger.application;

import com.example.finance.ledger.application.ResolveEffectiveFxRate.ResolvedFxRate;
import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.FxRateFeedSettings;
import com.example.finance.ledger.domain.error.LedgerErrors.FxRateUnavailableException;
import com.example.finance.ledger.domain.journal.FxRateOverride;
import com.example.finance.ledger.domain.journal.FxRateQuote;
import com.example.finance.ledger.domain.journal.repository.FxRateOverrideRepository;
import com.example.finance.ledger.domain.journal.repository.FxRateQuoteRepository;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.LedgerReportingCurrency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link ResolveEffectiveFxRate} (24th increment, TASK-FIN-BE-032, ADR-002 D3/D4;
 * extended 28th increment, TASK-FIN-BE-042 — per-tenant contract-rate override). Pure — no
 * Testcontainers; mocks the cache repo + override repo + feed settings + clock. Proves the
 * precedence (manual > per-tenant override > feed; manual wins → net-zero), the override layer
 * (present → override + {@code source=override:contract}, {@code fromFeed=false}; absent → feed
 * path UNCHANGED, net-zero), tenant-scoping (tenant A's override does NOT apply to tenant B), the
 * fail-closed branches (disabled / no quote / stale), the fresh fallback (provenance string), and
 * the staleness boundary (exactly maxAge = fresh, AC-4).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ResolveEffectiveFxRateTest {

    private static final String TENANT_A = "finance";
    private static final String TENANT_B = "other-tenant";
    private static final Currency BASE = LedgerReportingCurrency.BASE; // KRW
    private static final Currency USD = Currency.USD;
    private static final Instant NOW = Instant.parse("2026-06-30T12:00:00Z");
    private static final Duration MAX_AGE = Duration.ofMinutes(1440); // 24h

    @Mock FxRateQuoteRepository fxRateQuoteRepository;
    @Mock FxRateOverrideRepository fxRateOverrideRepository;
    @Mock FxRateFeedSettings settings;
    @Mock ClockPort clock;

    ResolveEffectiveFxRate resolver;

    void initResolver() {
        resolver = new ResolveEffectiveFxRate(
                fxRateQuoteRepository, fxRateOverrideRepository, settings, clock);
    }

    private static FxRateQuote quote(String rate, Instant asOf) {
        return FxRateQuote.of(BASE, USD, new BigDecimal(rate), asOf, "stub", asOf);
    }

    private static FxRateOverride override(String tenantId, String rate) {
        return FxRateOverride.of(tenantId, BASE, USD, new BigDecimal(rate), "user-1", NOW);
    }

    @Test
    @DisplayName("operator supplied a rate → manual (fromFeed=false); override + cache never consulted")
    void providedRateIsManual() {
        initResolver();

        ResolvedFxRate resolved = resolver.resolve(TENANT_A, BASE, USD, new BigDecimal("13.7"));

        assertThat(resolved.fromFeed()).isFalse();
        assertThat(resolved.rate()).isEqualByComparingTo("13.7");
        assertThat(resolved.sourceDescription()).isEqualTo("manual");
        verify(fxRateOverrideRepository, never()).findOverride(any(), any(), any());
        verify(fxRateQuoteRepository, never()).findLatest(any(), any());
        verify(settings, never()).feedEnabled();
    }

    @Test
    @DisplayName("omitted + per-tenant override present → override rate (source=override:contract, "
            + "fromFeed=false); cache + feed-settings never consulted")
    void omittedOverridePresentUsesContractRate() {
        initResolver();
        when(fxRateOverrideRepository.findOverride(TENANT_A, BASE, USD))
                .thenReturn(Optional.of(override(TENANT_A, "1325.5")));

        ResolvedFxRate resolved = resolver.resolve(TENANT_A, BASE, USD, null);

        assertThat(resolved.fromFeed()).isFalse();
        assertThat(resolved.rate()).isEqualByComparingTo("1325.5");
        assertThat(resolved.sourceDescription()).isEqualTo("override:contract");
        verify(fxRateQuoteRepository, never()).findLatest(any(), any());
        verify(settings, never()).feedEnabled();
    }

    @Test
    @DisplayName("omitted + override present takes precedence over a fresh feed quote (override > feed)")
    void overrideWinsOverFreshFeed() {
        initResolver();
        when(fxRateOverrideRepository.findOverride(TENANT_A, BASE, USD))
                .thenReturn(Optional.of(override(TENANT_A, "1400")));

        ResolvedFxRate resolved = resolver.resolve(TENANT_A, BASE, USD, null);

        // The feed is NEVER consulted — the contract rate shadows the market rate.
        assertThat(resolved.rate()).isEqualByComparingTo("1400");
        assertThat(resolved.sourceDescription()).isEqualTo("override:contract");
        verify(fxRateQuoteRepository, never()).findLatest(any(), any());
    }

    @Test
    @DisplayName("tenant-scoped (AC-3): tenant A's override does NOT apply to tenant B — B falls "
            + "through to the feed")
    void overrideIsTenantScoped() {
        initResolver();
        // Tenant A has a contract rate; tenant B has none (the lookup is keyed by tenant B).
        when(fxRateOverrideRepository.findOverride(TENANT_B, BASE, USD)).thenReturn(Optional.empty());
        when(settings.feedEnabled()).thenReturn(true);
        when(settings.staleAfter()).thenReturn(MAX_AGE);
        when(clock.now()).thenReturn(NOW);
        Instant freshAsOf = NOW.minusSeconds(60);
        when(fxRateQuoteRepository.findLatest(BASE, USD))
                .thenReturn(Optional.of(quote("1300", freshAsOf)));

        ResolvedFxRate resolved = resolver.resolve(TENANT_B, BASE, USD, null);

        // Tenant B sees the market feed rate (1300), NOT tenant A's contract rate — no leak.
        assertThat(resolved.fromFeed()).isTrue();
        assertThat(resolved.rate()).isEqualByComparingTo("1300");
        assertThat(resolved.sourceDescription()).isEqualTo("feed:stub@" + freshAsOf);
    }

    @Test
    @DisplayName("omitted + NO override + feed disabled → FX_RATE_UNAVAILABLE (net-zero feed fallthrough)")
    void omittedNoOverrideFeedDisabledFailsClosed() {
        initResolver();
        when(fxRateOverrideRepository.findOverride(TENANT_A, BASE, USD)).thenReturn(Optional.empty());
        when(settings.feedEnabled()).thenReturn(false);

        assertThatThrownBy(() -> resolver.resolve(TENANT_A, BASE, USD, null))
                .isInstanceOf(FxRateUnavailableException.class);
        verify(fxRateQuoteRepository, never()).findLatest(any(), any());
    }

    @Test
    @DisplayName("omitted + NO override + enabled + no cached quote → FX_RATE_UNAVAILABLE")
    void omittedNoOverrideEnabledNoQuoteFailsClosed() {
        initResolver();
        when(fxRateOverrideRepository.findOverride(TENANT_A, BASE, USD)).thenReturn(Optional.empty());
        when(settings.feedEnabled()).thenReturn(true);
        when(fxRateQuoteRepository.findLatest(BASE, USD)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(TENANT_A, BASE, USD, null))
                .isInstanceOf(FxRateUnavailableException.class);
    }

    @Test
    @DisplayName("omitted + NO override + enabled + stale quote (now − asOf > maxAge) → FX_RATE_UNAVAILABLE")
    void omittedNoOverrideEnabledStaleFailsClosed() {
        initResolver();
        when(fxRateOverrideRepository.findOverride(TENANT_A, BASE, USD)).thenReturn(Optional.empty());
        when(settings.feedEnabled()).thenReturn(true);
        when(settings.staleAfter()).thenReturn(MAX_AGE);
        when(clock.now()).thenReturn(NOW);
        // asOf one second older than exactly maxAge → stale.
        Instant staleAsOf = NOW.minus(MAX_AGE).minusSeconds(1);
        when(fxRateQuoteRepository.findLatest(BASE, USD)).thenReturn(Optional.of(quote("1300", staleAsOf)));

        assertThatThrownBy(() -> resolver.resolve(TENANT_A, BASE, USD, null))
                .isInstanceOf(FxRateUnavailableException.class);
    }

    @Test
    @DisplayName("omitted + NO override + enabled + fresh quote → fromFeed=true (feed path UNCHANGED, net-zero)")
    void omittedNoOverrideEnabledFreshUsesCache() {
        initResolver();
        when(fxRateOverrideRepository.findOverride(TENANT_A, BASE, USD)).thenReturn(Optional.empty());
        when(settings.feedEnabled()).thenReturn(true);
        when(settings.staleAfter()).thenReturn(MAX_AGE);
        when(clock.now()).thenReturn(NOW);
        Instant freshAsOf = NOW.minusSeconds(60); // 1 minute old → well within 24h
        when(fxRateQuoteRepository.findLatest(BASE, USD)).thenReturn(Optional.of(quote("1300", freshAsOf)));

        ResolvedFxRate resolved = resolver.resolve(TENANT_A, BASE, USD, null);

        assertThat(resolved.fromFeed()).isTrue();
        assertThat(resolved.rate()).isEqualByComparingTo("1300");
        assertThat(resolved.sourceDescription()).isEqualTo("feed:stub@" + freshAsOf);
    }

    @Test
    @DisplayName("staleness boundary: now − asOf == maxAge exactly → FRESH (inclusive ≤, AC-4)")
    void staleBoundaryExactlyMaxAgeIsFresh() {
        initResolver();
        when(fxRateOverrideRepository.findOverride(TENANT_A, BASE, USD)).thenReturn(Optional.empty());
        when(settings.feedEnabled()).thenReturn(true);
        when(settings.staleAfter()).thenReturn(MAX_AGE);
        when(clock.now()).thenReturn(NOW);
        Instant exactlyMaxAge = NOW.minus(MAX_AGE); // now − asOf == maxAge → fresh
        when(fxRateQuoteRepository.findLatest(BASE, USD))
                .thenReturn(Optional.of(quote("1300", exactlyMaxAge)));

        ResolvedFxRate resolved = resolver.resolve(TENANT_A, BASE, USD, null);

        assertThat(resolved.fromFeed()).isTrue();
        assertThat(resolved.rate()).isEqualByComparingTo("1300");
    }
}
