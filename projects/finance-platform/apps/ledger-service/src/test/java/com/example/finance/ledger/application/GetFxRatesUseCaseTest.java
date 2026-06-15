package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.FxRateFeedSettings;
import com.example.finance.ledger.application.view.FxRateView;
import com.example.finance.ledger.application.view.FxRatesView;
import com.example.finance.ledger.domain.journal.FxRateQuote;
import com.example.finance.ledger.domain.journal.repository.FxRateQuoteRepository;
import com.example.finance.ledger.domain.money.Currency;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GetFxRatesUseCase} (25th increment — TASK-FIN-BE-033).
 * Verifies staleness boundary (AC-3), sorting (AC-2), feedEnabled passthrough (AC-4),
 * and empty cache (AC-1). No Spring context — STRICT_STUBS.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class GetFxRatesUseCaseTest {

    /** Default max-age = 24 h (1440 min). */
    private static final Duration STALE_AFTER = Duration.ofMinutes(1440);

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");

    @Mock FxRateQuoteRepository fxRateQuoteRepository;
    @Mock FxRateFeedSettings settings;
    @Mock ClockPort clock;

    GetFxRatesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetFxRatesUseCase(fxRateQuoteRepository, settings, clock);
        when(clock.now()).thenReturn(NOW);
        when(settings.staleAfter()).thenReturn(STALE_AFTER);
    }

    // -------------------------------------------------------------------------
    // AC-1: empty cache
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("empty cache → feedEnabled passthrough + rates [] (200, not 404)")
    void emptyCacheReturnsFeedEnabledAndEmptyList() {
        when(fxRateQuoteRepository.findAll()).thenReturn(List.of());
        when(settings.feedEnabled()).thenReturn(true);

        FxRatesView view = useCase.get();

        assertThat(view.feedEnabled()).isTrue();
        assertThat(view.rates()).isEmpty();
    }

    // -------------------------------------------------------------------------
    // AC-4: feedEnabled passthrough
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("feedEnabled=false is propagated even when quotes exist")
    void feedDisabledPassthrough() {
        Instant asOf = NOW.minus(Duration.ofHours(1));
        FxRateQuote q = FxRateQuote.of(Currency.KRW, Currency.USD,
                new BigDecimal("13.5"), asOf, "stub", asOf);
        when(fxRateQuoteRepository.findAll()).thenReturn(List.of(q));
        when(settings.feedEnabled()).thenReturn(false);

        FxRatesView view = useCase.get();

        assertThat(view.feedEnabled()).isFalse();
        assertThat(view.rates()).hasSize(1);
    }

    // -------------------------------------------------------------------------
    // AC-2: sorting
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("quotes are sorted (baseCurrency, foreignCurrency) ASC")
    void sortingIsDeterministic() {
        Instant asOf = NOW.minus(Duration.ofHours(1));
        FxRateQuote qUsd = FxRateQuote.of(Currency.KRW, Currency.USD,
                new BigDecimal("13.5"), asOf, "stub", asOf);
        FxRateQuote qEur = FxRateQuote.of(Currency.KRW, Currency.EUR,
                new BigDecimal("14.2"), asOf, "stub", asOf);
        FxRateQuote qJpy = FxRateQuote.of(Currency.KRW, Currency.JPY,
                new BigDecimal("0.0093"), asOf, "stub", asOf);

        // Repo returns in reversed order; the use case must sort.
        when(fxRateQuoteRepository.findAll()).thenReturn(List.of(qUsd, qJpy, qEur));
        when(settings.feedEnabled()).thenReturn(true);

        FxRatesView view = useCase.get();

        List<FxRateView> rates = view.rates();
        assertThat(rates).hasSize(3);
        // All base = "KRW"; foreign sorted: EUR < JPY < USD (alphabetical)
        assertThat(rates.get(0).foreignCurrency()).isEqualTo("EUR");
        assertThat(rates.get(1).foreignCurrency()).isEqualTo("JPY");
        assertThat(rates.get(2).foreignCurrency()).isEqualTo("USD");
    }

    // -------------------------------------------------------------------------
    // AC-3: staleness boundary
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("quote exactly staleAfter old → stale=false (boundary is fresh)")
    void exactBoundaryIsFresh() {
        // asOf = now - staleAfter → age == staleAfter → compareTo == 0 → fresh
        Instant asOf = NOW.minus(STALE_AFTER);
        FxRateQuote q = FxRateQuote.of(Currency.KRW, Currency.USD,
                new BigDecimal("13.5"), asOf, "stub", asOf);
        when(fxRateQuoteRepository.findAll()).thenReturn(List.of(q));
        when(settings.feedEnabled()).thenReturn(true);

        FxRateView rate = useCase.get().rates().get(0);

        assertThat(rate.stale()).isFalse();
        assertThat(rate.ageSeconds()).isEqualTo(STALE_AFTER.getSeconds());
    }

    @Test
    @DisplayName("quote one second older than staleAfter → stale=true")
    void oneSecondOverBoundaryIsStale() {
        // asOf = now - staleAfter - 1s → age > staleAfter → stale
        Instant asOf = NOW.minus(STALE_AFTER).minusSeconds(1);
        FxRateQuote q = FxRateQuote.of(Currency.KRW, Currency.USD,
                new BigDecimal("13.5"), asOf, "stub", asOf);
        when(fxRateQuoteRepository.findAll()).thenReturn(List.of(q));
        when(settings.feedEnabled()).thenReturn(true);

        FxRateView rate = useCase.get().rates().get(0);

        assertThat(rate.stale()).isTrue();
        assertThat(rate.ageSeconds()).isEqualTo(STALE_AFTER.getSeconds() + 1);
    }

    @Test
    @DisplayName("fresh quote (1 hour old) → stale=false, ageSeconds=3600")
    void freshQuoteWithinWindow() {
        Instant asOf = NOW.minus(Duration.ofHours(1));
        FxRateQuote q = FxRateQuote.of(Currency.KRW, Currency.USD,
                new BigDecimal("13.5"), asOf, "http:provider", NOW.minus(Duration.ofMinutes(59)));
        when(fxRateQuoteRepository.findAll()).thenReturn(List.of(q));
        when(settings.feedEnabled()).thenReturn(true);

        FxRateView rate = useCase.get().rates().get(0);

        assertThat(rate.stale()).isFalse();
        assertThat(rate.ageSeconds()).isEqualTo(3600L);
        assertThat(rate.baseCurrency()).isEqualTo("KRW");
        assertThat(rate.foreignCurrency()).isEqualTo("USD");
        assertThat(rate.rate()).isEqualByComparingTo(new BigDecimal("13.5"));
        assertThat(rate.source()).isEqualTo("http:provider");
    }

    @Test
    @DisplayName("stale quote (2 days old) → stale=true")
    void twoDayOldQuoteIsStale() {
        Instant asOf = NOW.minus(Duration.ofDays(2));
        FxRateQuote q = FxRateQuote.of(Currency.KRW, Currency.USD,
                new BigDecimal("13.5"), asOf, "stub", asOf);
        when(fxRateQuoteRepository.findAll()).thenReturn(List.of(q));
        when(settings.feedEnabled()).thenReturn(true);

        FxRateView rate = useCase.get().rates().get(0);

        assertThat(rate.stale()).isTrue();
        assertThat(rate.ageSeconds()).isEqualTo(Duration.ofDays(2).getSeconds());
    }

    @Test
    @DisplayName("quote with asOf in the future → ageSeconds negative, stale=false")
    void futureAsOfIsNegativeAgeAndFresh() {
        // Clock skew edge case — do not clamp; expose for diagnostic transparency
        Instant asOf = NOW.plusSeconds(300);
        FxRateQuote q = FxRateQuote.of(Currency.KRW, Currency.USD,
                new BigDecimal("13.5"), asOf, "stub", asOf);
        when(fxRateQuoteRepository.findAll()).thenReturn(List.of(q));
        when(settings.feedEnabled()).thenReturn(true);

        FxRateView rate = useCase.get().rates().get(0);

        assertThat(rate.ageSeconds()).isEqualTo(-300L);
        assertThat(rate.stale()).isFalse();
    }

    // -------------------------------------------------------------------------
    // AC-6: rate field passthrough
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("rate is passed through as BigDecimal (DTO converts to string)")
    void rateIsPassedThroughExact() {
        BigDecimal exactRate = new BigDecimal("1350.00000000");
        Instant asOf = NOW.minus(Duration.ofMinutes(10));
        FxRateQuote q = FxRateQuote.of(Currency.KRW, Currency.USD,
                exactRate, asOf, "stub", asOf);
        when(fxRateQuoteRepository.findAll()).thenReturn(List.of(q));
        when(settings.feedEnabled()).thenReturn(true);

        FxRateView rate = useCase.get().rates().get(0);

        assertThat(rate.rate()).isEqualByComparingTo(exactRate);
    }
}
