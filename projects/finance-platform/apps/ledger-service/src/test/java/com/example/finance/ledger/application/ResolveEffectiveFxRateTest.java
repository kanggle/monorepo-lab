package com.example.finance.ledger.application;

import com.example.finance.ledger.application.ResolveEffectiveFxRate.ResolvedFxRate;
import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.FxRateFeedSettings;
import com.example.finance.ledger.domain.error.LedgerErrors.FxRateUnavailableException;
import com.example.finance.ledger.domain.journal.FxRateQuote;
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
 * Unit test for {@link ResolveEffectiveFxRate} (24th increment, TASK-FIN-BE-032, ADR-002 D3/D4).
 * Pure — no Testcontainers; mocks the cache repo + feed settings + clock. Proves the precedence
 * (manual wins → net-zero), the fail-closed branches (disabled / no quote / stale), the fresh
 * fallback (provenance string), and the staleness boundary (exactly maxAge = fresh, AC-4).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ResolveEffectiveFxRateTest {

    private static final Currency BASE = LedgerReportingCurrency.BASE; // KRW
    private static final Currency USD = Currency.USD;
    private static final Instant NOW = Instant.parse("2026-06-30T12:00:00Z");
    private static final Duration MAX_AGE = Duration.ofMinutes(1440); // 24h

    @Mock FxRateQuoteRepository fxRateQuoteRepository;
    @Mock FxRateFeedSettings settings;
    @Mock ClockPort clock;

    ResolveEffectiveFxRate resolver;

    void initResolver() {
        resolver = new ResolveEffectiveFxRate(fxRateQuoteRepository, settings, clock);
    }

    private static FxRateQuote quote(String rate, Instant asOf) {
        return FxRateQuote.of(BASE, USD, new BigDecimal(rate), asOf, "stub", asOf);
    }

    @Test
    @DisplayName("operator supplied a rate → manual (fromFeed=false), the cache is never consulted")
    void providedRateIsManual() {
        initResolver();

        ResolvedFxRate resolved = resolver.resolve(BASE, USD, new BigDecimal("13.7"));

        assertThat(resolved.fromFeed()).isFalse();
        assertThat(resolved.rate()).isEqualByComparingTo("13.7");
        assertThat(resolved.sourceDescription()).isEqualTo("manual");
        verify(fxRateQuoteRepository, never()).findLatest(any(), any());
        verify(settings, never()).feedEnabled();
    }

    @Test
    @DisplayName("omitted + feed disabled → FX_RATE_UNAVAILABLE (fail-closed), cache not read")
    void omittedFeedDisabledFailsClosed() {
        initResolver();
        when(settings.feedEnabled()).thenReturn(false);

        assertThatThrownBy(() -> resolver.resolve(BASE, USD, null))
                .isInstanceOf(FxRateUnavailableException.class);
        verify(fxRateQuoteRepository, never()).findLatest(any(), any());
    }

    @Test
    @DisplayName("omitted + enabled + no cached quote → FX_RATE_UNAVAILABLE")
    void omittedEnabledNoQuoteFailsClosed() {
        initResolver();
        when(settings.feedEnabled()).thenReturn(true);
        when(fxRateQuoteRepository.findLatest(BASE, USD)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.resolve(BASE, USD, null))
                .isInstanceOf(FxRateUnavailableException.class);
    }

    @Test
    @DisplayName("omitted + enabled + stale quote (now − asOf > maxAge) → FX_RATE_UNAVAILABLE")
    void omittedEnabledStaleFailsClosed() {
        initResolver();
        when(settings.feedEnabled()).thenReturn(true);
        when(settings.staleAfter()).thenReturn(MAX_AGE);
        when(clock.now()).thenReturn(NOW);
        // asOf one second older than exactly maxAge → stale.
        Instant staleAsOf = NOW.minus(MAX_AGE).minusSeconds(1);
        when(fxRateQuoteRepository.findLatest(BASE, USD)).thenReturn(Optional.of(quote("1300", staleAsOf)));

        assertThatThrownBy(() -> resolver.resolve(BASE, USD, null))
                .isInstanceOf(FxRateUnavailableException.class);
    }

    @Test
    @DisplayName("omitted + enabled + fresh quote → fromFeed=true with the quote's rate + provenance")
    void omittedEnabledFreshUsesCache() {
        initResolver();
        when(settings.feedEnabled()).thenReturn(true);
        when(settings.staleAfter()).thenReturn(MAX_AGE);
        when(clock.now()).thenReturn(NOW);
        Instant freshAsOf = NOW.minusSeconds(60); // 1 minute old → well within 24h
        when(fxRateQuoteRepository.findLatest(BASE, USD)).thenReturn(Optional.of(quote("1300", freshAsOf)));

        ResolvedFxRate resolved = resolver.resolve(BASE, USD, null);

        assertThat(resolved.fromFeed()).isTrue();
        assertThat(resolved.rate()).isEqualByComparingTo("1300");
        assertThat(resolved.sourceDescription()).isEqualTo("feed:stub@" + freshAsOf);
    }

    @Test
    @DisplayName("staleness boundary: now − asOf == maxAge exactly → FRESH (inclusive ≤, AC-4)")
    void staleBoundaryExactlyMaxAgeIsFresh() {
        initResolver();
        when(settings.feedEnabled()).thenReturn(true);
        when(settings.staleAfter()).thenReturn(MAX_AGE);
        when(clock.now()).thenReturn(NOW);
        Instant exactlyMaxAge = NOW.minus(MAX_AGE); // now − asOf == maxAge → fresh
        when(fxRateQuoteRepository.findLatest(BASE, USD))
                .thenReturn(Optional.of(quote("1300", exactlyMaxAge)));

        ResolvedFxRate resolved = resolver.resolve(BASE, USD, null);

        assertThat(resolved.fromFeed()).isTrue();
        assertThat(resolved.rate()).isEqualByComparingTo("1300");
    }
}
