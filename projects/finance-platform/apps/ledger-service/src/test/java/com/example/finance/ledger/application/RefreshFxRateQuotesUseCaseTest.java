package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.FxRateFeedSettings;
import com.example.finance.ledger.application.port.outbound.FxRateProviderPort;
import com.example.finance.ledger.application.port.outbound.FxRateProviderPort.RateQuote;
import com.example.finance.ledger.domain.journal.FxRateQuote;
import com.example.finance.ledger.domain.journal.FxRateQuoteHistory;
import com.example.finance.ledger.domain.journal.repository.FxRateQuoteHistoryRepository;
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
 * Unit test for {@link RefreshFxRateQuotesUseCase} (AC-5 load + AC-6 per-pair isolation +
 * TASK-FIN-BE-039 append-only history trail). The provider port + repositories + settings + clock
 * are mocked — no Spring context, no Docker. All tests in this class are Docker-free.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class RefreshFxRateQuotesUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-06-15T00:00:00Z");
    private static final Instant AS_OF = Instant.parse("2026-06-14T00:00:00Z");

    @Mock FxRateProviderPort fxRateProviderPort;
    @Mock FxRateQuoteRepository fxRateQuoteRepository;
    @Mock FxRateQuoteHistoryRepository fxRateQuoteHistoryRepository;
    @Mock FxRateFeedSettings settings;
    @Mock ClockPort clock;

    RefreshFxRateQuotesUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RefreshFxRateQuotesUseCase(
                fxRateProviderPort, fxRateQuoteRepository, fxRateQuoteHistoryRepository,
                settings, clock);
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

        // History rows match the upserted pairs 1:1 (AC — N upserts → N history rows).
        ArgumentCaptor<FxRateQuoteHistory> appended =
                ArgumentCaptor.forClass(FxRateQuoteHistory.class);
        verify(fxRateQuoteHistoryRepository, times(2)).append(appended.capture());
        assertThat(appended.getAllValues())
                .extracting(FxRateQuoteHistory::foreignCurrency)
                .containsExactlyInAnyOrder("USD", "EUR");
        assertThat(appended.getAllValues())
                .allSatisfy(h -> {
                    assertThat(h.baseCurrency()).isEqualTo("KRW");
                    assertThat(h.fetchedAt()).isEqualTo(NOW);
                    assertThat(h.asOf()).isEqualTo(AS_OF);
                });
    }

    @Test
    void historyRowCarriesSameRateAsOfSourceFetchedAtAsLatestUpsert() {
        when(settings.pairs()).thenReturn(List.of("USD"));
        BigDecimal rate = new BigDecimal("1300.12345678");
        when(fxRateProviderPort.latestQuote(Currency.KRW, Currency.USD))
                .thenReturn(Optional.of(new RateQuote(rate, AS_OF, "frankfurter")));

        useCase.refresh();

        ArgumentCaptor<FxRateQuote> savedQuote = ArgumentCaptor.forClass(FxRateQuote.class);
        ArgumentCaptor<FxRateQuoteHistory> appendedHistory =
                ArgumentCaptor.forClass(FxRateQuoteHistory.class);
        verify(fxRateQuoteRepository).save(savedQuote.capture());
        verify(fxRateQuoteHistoryRepository).append(appendedHistory.capture());

        FxRateQuote q = savedQuote.getValue();
        FxRateQuoteHistory h = appendedHistory.getValue();
        assertThat(h.rate()).isEqualByComparingTo(q.rate());
        assertThat(h.asOf()).isEqualTo(q.asOf());
        assertThat(h.source()).isEqualTo(q.source());
        assertThat(h.fetchedAt()).isEqualTo(q.fetchedAt());
        assertThat(h.baseCurrency()).isEqualTo(q.baseCurrency());
        assertThat(h.foreignCurrency()).isEqualTo(q.foreignCurrency());
    }

    /**
     * Append-only across two runs (AC — TASK-FIN-BE-039): two successive refresh() calls for the
     * same pair (different as_of / fetched_at) produce TWO history appends but the latest-upsert
     * path is called twice too (last-write-wins). This unit test uses a mock clock that returns
     * different instants per call to simulate advancing time between runs.
     */
    @Test
    void appendOnlyAcrossTwoRunsSamePairTwoHistoryRows() {
        Instant run1Now = Instant.parse("2026-06-15T08:00:00Z");
        Instant run2Now = Instant.parse("2026-06-15T09:00:00Z");
        Instant asOf1 = Instant.parse("2026-06-14T00:00:00Z");
        Instant asOf2 = Instant.parse("2026-06-15T00:00:00Z");

        when(settings.pairs()).thenReturn(List.of("USD"));
        when(fxRateProviderPort.latestQuote(Currency.KRW, Currency.USD))
                .thenReturn(Optional.of(new RateQuote(new BigDecimal("1300.0"), asOf1, "stub")))
                .thenReturn(Optional.of(new RateQuote(new BigDecimal("1305.0"), asOf2, "stub")));
        when(clock.now())
                .thenReturn(run1Now)
                .thenReturn(run2Now);

        // Run 1
        int upserted1 = useCase.refresh();
        // Run 2 — same pair, newer rate / asOf / fetchedAt
        int upserted2 = useCase.refresh();

        assertThat(upserted1).isEqualTo(1);
        assertThat(upserted2).isEqualTo(1);

        // Latest-upsert called twice (last-write-wins; fx_rate_quote row count = 1).
        verify(fxRateQuoteRepository, times(2)).save(any(FxRateQuote.class));

        // History append called twice → two rows for the same pair (append-only).
        ArgumentCaptor<FxRateQuoteHistory> appended =
                ArgumentCaptor.forClass(FxRateQuoteHistory.class);
        verify(fxRateQuoteHistoryRepository, times(2)).append(appended.capture());
        List<FxRateQuoteHistory> historyRows = appended.getAllValues();
        assertThat(historyRows).hasSize(2);
        // Both rows are for USD.
        assertThat(historyRows).allSatisfy(h -> assertThat(h.foreignCurrency()).isEqualTo("USD"));
        // First row = run1Now / asOf1; second row = run2Now / asOf2.
        assertThat(historyRows.get(0).fetchedAt()).isEqualTo(run1Now);
        assertThat(historyRows.get(0).asOf()).isEqualTo(asOf1);
        assertThat(historyRows.get(1).fetchedAt()).isEqualTo(run2Now);
        assertThat(historyRows.get(1).asOf()).isEqualTo(asOf2);
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
        // History is also appended for the successful pairs only.
        verify(fxRateQuoteHistoryRepository, times(2)).append(any(FxRateQuoteHistory.class));
    }

    @Test
    void noPairsConfiguredWritesNothing() {
        when(settings.pairs()).thenReturn(List.of());

        int upserted = useCase.refresh();

        assertThat(upserted).isZero();
        verify(fxRateQuoteRepository, never()).save(any());
        verify(fxRateQuoteHistoryRepository, never()).append(any());
    }
}
