package com.example.finance.ledger.application;

import com.example.finance.ledger.application.view.FxRateHistorySummaryView;
import com.example.finance.ledger.application.view.FxRateHistoryView;
import com.example.finance.ledger.domain.journal.FxRateQuoteHistory;
import com.example.finance.ledger.domain.journal.repository.FxRateQuoteHistoryRepository;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.LedgerReportingCurrency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GetFxRateHistoryUseCase} (27th increment — TASK-FIN-BE-040).
 * Verifies limit normalisation, newest-first ordering passthrough, empty result for unknown
 * pairs, and foreign code parse. No Spring context — STRICT_STUBS.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class GetFxRateHistoryUseCaseTest {

    private static final Instant T1 = Instant.parse("2026-06-15T06:00:00Z");
    private static final Instant T2 = Instant.parse("2026-06-15T07:00:00Z"); // newer

    @Mock
    FxRateQuoteHistoryRepository repository;

    GetFxRateHistoryUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetFxRateHistoryUseCase(repository);
    }

    // -------------------------------------------------------------------------
    // Limit normalisation
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "normaliseLimit({0}) → {1}")
    @CsvSource({
            ",50",      // null → 50
            "50,50",    // within range unchanged
            "0,1",      // zero → floor 1
            "-1,1",     // negative → floor 1
            "500,500",  // cap boundary → 500
            "501,500",  // over cap → 500
            "1,1",      // floor boundary → 1
    })
    @DisplayName("limit normalisation: null→50, ≤0→1, >500→500, within range unchanged")
    void normaliseLimitEdgeCases(Integer raw, int expected) {
        assertThat(GetFxRateHistoryUseCase.normaliseLimit(raw)).isEqualTo(expected);
    }

    @Test
    @DisplayName("null limit → default 50 passed to repository")
    void nullLimitUsesDefault() {
        when(repository.findHistory(LedgerReportingCurrency.BASE, Currency.USD, 50))
                .thenReturn(List.of());

        useCase.get("USD", null);

        verify(repository).findHistory(LedgerReportingCurrency.BASE, Currency.USD, 50);
    }

    @Test
    @DisplayName("limit ≤ 0 → floored to 1")
    void zeroLimitIsFlooredToOne() {
        when(repository.findHistory(LedgerReportingCurrency.BASE, Currency.USD, 1))
                .thenReturn(List.of());

        useCase.get("USD", 0);

        verify(repository).findHistory(LedgerReportingCurrency.BASE, Currency.USD, 1);
    }

    @Test
    @DisplayName("limit > 500 → capped to 500")
    void overCapLimitIsCappedAt500() {
        when(repository.findHistory(LedgerReportingCurrency.BASE, Currency.USD, 500))
                .thenReturn(List.of());

        useCase.get("USD", 999);

        verify(repository).findHistory(LedgerReportingCurrency.BASE, Currency.USD, 500);
    }

    // -------------------------------------------------------------------------
    // Ordering passthrough (newest-first)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("repository rows returned in repo order (newest first) — ordering not re-sorted")
    void rowsReturnedInRepoOrder() {
        // T2 (newer) should appear first — repo guarantees DESC order; use-case must not re-sort.
        FxRateQuoteHistory newerRow =
                FxRateQuoteHistory.of(Currency.KRW, Currency.USD,
                        new BigDecimal("13.60000000"), T2, "stub", T2);
        FxRateQuoteHistory olderRow =
                FxRateQuoteHistory.of(Currency.KRW, Currency.USD,
                        new BigDecimal("13.50000000"), T1, "stub", T1);

        when(repository.findHistory(LedgerReportingCurrency.BASE, Currency.USD, 50))
                .thenReturn(List.of(newerRow, olderRow)); // repo already sorted newest-first

        FxRateHistorySummaryView view = useCase.get("USD", null);

        assertThat(view.quotes()).hasSize(2);
        FxRateHistoryView first = view.quotes().get(0);
        FxRateHistoryView second = view.quotes().get(1);
        assertThat(first.fetchedAt()).isEqualTo(T2);
        assertThat(second.fetchedAt()).isEqualTo(T1);
        // rate is preserved as BigDecimal
        assertThat(first.rate()).isEqualByComparingTo(new BigDecimal("13.60000000"));
        assertThat(second.rate()).isEqualByComparingTo(new BigDecimal("13.50000000"));
    }

    // -------------------------------------------------------------------------
    // Empty result
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("never-polled pair → empty quotes list (200, not an exception)")
    void neverPolledPairReturnsEmptyList() {
        when(repository.findHistory(LedgerReportingCurrency.BASE, Currency.JPY, 50))
                .thenReturn(List.of());

        FxRateHistorySummaryView view = useCase.get("JPY", null);

        assertThat(view.quotes()).isEmpty();
        assertThat(view.baseCurrency()).isEqualTo("KRW");
        assertThat(view.foreignCurrency()).isEqualTo("JPY");
    }

    // -------------------------------------------------------------------------
    // Foreign code parse / unknown currency
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("unsupported 3-letter code → empty-200 (no repository call)")
    void unsupportedCurrencyReturnsEmpty() {
        // "XYZ" is a well-formed 3-letter code but not in Currency enum → UnsupportedCurrencyException
        FxRateHistorySummaryView view = useCase.get("XYZ", null);

        assertThat(view.quotes()).isEmpty();
        assertThat(view.foreignCurrency()).isEqualTo("XYZ");
        // Repository must NOT be called for an unsupported code
        verify(repository, org.mockito.Mockito.never()).findHistory(any(), any(), anyInt());
    }

    @Test
    @DisplayName("lowercase code is normalised before lookup")
    void lowercaseCodeIsNormalised() {
        when(repository.findHistory(LedgerReportingCurrency.BASE, Currency.USD, 50))
                .thenReturn(List.of());

        FxRateHistorySummaryView view = useCase.get("usd", null);

        assertThat(view.foreignCurrency()).isEqualTo("USD");
        verify(repository).findHistory(eq(LedgerReportingCurrency.BASE), eq(Currency.USD), eq(50));
    }

    @Test
    @DisplayName("null foreignCode → empty-200 (Currency.of throws on null)")
    void nullForeignCodeReturnsEmpty() {
        FxRateHistorySummaryView view = useCase.get(null, null);

        assertThat(view.quotes()).isEmpty();
        verify(repository, org.mockito.Mockito.never()).findHistory(any(), any(), anyInt());
    }

    // -------------------------------------------------------------------------
    // View field mapping
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("view fields — rate, asOf, fetchedAt, source mapped correctly")
    void viewFieldsAreMappedCorrectly() {
        Instant asOf = Instant.parse("2026-06-15T06:00:00Z");
        Instant fetchedAt = Instant.parse("2026-06-15T06:00:05Z");
        FxRateQuoteHistory row =
                FxRateQuoteHistory.of(Currency.KRW, Currency.EUR,
                        new BigDecimal("14.20000000"), asOf, "http:provider", fetchedAt);

        when(repository.findHistory(LedgerReportingCurrency.BASE, Currency.EUR, 50))
                .thenReturn(List.of(row));

        FxRateHistorySummaryView view = useCase.get("EUR", null);

        assertThat(view.baseCurrency()).isEqualTo("KRW");
        assertThat(view.foreignCurrency()).isEqualTo("EUR");
        assertThat(view.quotes()).hasSize(1);
        FxRateHistoryView q = view.quotes().get(0);
        assertThat(q.rate()).isEqualByComparingTo(new BigDecimal("14.20000000"));
        assertThat(q.asOf()).isEqualTo(asOf);
        assertThat(q.fetchedAt()).isEqualTo(fetchedAt);
        assertThat(q.source()).isEqualTo("http:provider");
    }
}
