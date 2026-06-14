package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.FxRateFeedSettings;
import com.example.finance.ledger.application.port.outbound.FxRateProviderPort;
import com.example.finance.ledger.application.port.outbound.FxRateProviderPort.RateQuote;
import com.example.finance.ledger.domain.journal.FxRateQuote;
import com.example.finance.ledger.domain.journal.repository.FxRateQuoteRepository;
import com.example.finance.ledger.domain.money.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link RefreshFxRateQuotesUseCase} (AC-5 load + AC-6 per-pair isolation). The
 * provider port + repository + settings + clock are mocked — no Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class RefreshFxRateQuotesUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-06-15T00:00:00Z");
    private static final Instant AS_OF = Instant.parse("2026-06-14T00:00:00Z");

    @Mock FxRateProviderPort fxRateProviderPort;
    @Mock FxRateQuoteRepository fxRateQuoteRepository;
    @Mock FxRateFeedSettings settings;
    @Mock ClockPort clock;

    RefreshFxRateQuotesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RefreshFxRateQuotesUseCase(
                fxRateProviderPort, fxRateQuoteRepository, settings, clock);
        lenient().when(clock.now()).thenReturn(NOW);
    }

    @Test
    void upsertsEveryPresentPairAndSkipsEmptyOnes() {
        when(settings.pairs()).thenReturn(List.of("USD", "EUR", "JPY"));
        when(fxRateProviderPort.latestQuote(Currency.KRW, Currency.USD))
                .thenReturn(Optional.of(new RateQuote(new BigDecimal("1300.0"), AS_OF, "stub")));
        when(fxRateProviderPort.latestQuote(Currency.KRW, Currency.EUR))
                .thenReturn(Optional.of(new RateQuote(new BigDecimal("1450.0"), AS_OF, "stub")));
        // JPY unsupported → empty → skipped (no save).
        when(fxRateProviderPort.latestQuote(Currency.KRW, Currency.JPY))
                .thenReturn(Optional.empty());

        int upserted = useCase.refresh();

        assertThat(upserted).isEqualTo(2);
        ArgumentCaptor<FxRateQuote> saved = ArgumentCaptor.forClass(FxRateQuote.class);
        verify(fxRateQuoteRepository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues())
                .extracting(FxRateQuote::foreignCurrency)
                .containsExactlyInAnyOrder("USD", "EUR");
        assertThat(saved.getAllValues())
                .allSatisfy(q -> {
                    assertThat(q.baseCurrency()).isEqualTo("KRW");
                    assertThat(q.fetchedAt()).isEqualTo(NOW);
                    assertThat(q.asOf()).isEqualTo(AS_OF);
                });
    }

    @Test
    void oneFailingPairDoesNotAbortTheRest() {
        when(settings.pairs()).thenReturn(List.of("USD", "ZZZ", "EUR"));
        when(fxRateProviderPort.latestQuote(Currency.KRW, Currency.USD))
                .thenReturn(Optional.of(new RateQuote(new BigDecimal("1300.0"), AS_OF, "stub")));
        // "ZZZ" is unparseable → Currency.of throws inside the per-pair try → logged + skipped.
        when(fxRateProviderPort.latestQuote(Currency.KRW, Currency.EUR))
                .thenReturn(Optional.of(new RateQuote(new BigDecimal("1450.0"), AS_OF, "stub")));

        int upserted = useCase.refresh();

        // USD + EUR persisted despite the unparseable middle pair (AC-6).
        assertThat(upserted).isEqualTo(2);
        verify(fxRateQuoteRepository, times(2)).save(any(FxRateQuote.class));
    }

    @Test
    void noPairsConfiguredWritesNothing() {
        when(settings.pairs()).thenReturn(List.of());

        int upserted = useCase.refresh();

        assertThat(upserted).isZero();
        verify(fxRateQuoteRepository, never()).save(any());
    }
}
