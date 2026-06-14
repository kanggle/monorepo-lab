package com.example.finance.ledger.application;

import com.example.finance.ledger.application.view.FxPositionLotsView;
import com.example.finance.ledger.domain.journal.FxPositionLot;
import com.example.finance.ledger.domain.journal.repository.FxPositionLotRepository;
import com.example.finance.ledger.domain.money.Currency;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GetFxPositionLotsUseCase} (20th increment —
 * TASK-FIN-BE-028, AC-7). Verifies lot projection, summary computation, and
 * empty-position zero-return. Uses mocked {@link FxPositionLotRepository}
 * (STRICT_STUBS) — no Spring context.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class GetFxPositionLotsUseCaseTest {

    private static final String TENANT = "finance";
    private static final String ACCOUNT = "FX_LOTSREAD_USD_WALLET";
    private static final Currency USD = Currency.USD;
    private static final Instant T1 = Instant.parse("2026-01-01T00:00:00Z");
    private static final Instant T2 = Instant.parse("2026-01-02T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-06-14T00:00:00Z");

    @Mock
    FxPositionLotRepository fxPositionLotRepository;

    GetFxPositionLotsUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new GetFxPositionLotsUseCase(fxPositionLotRepository);
    }

    @Test
    @DisplayName("two open lots → view returns both ordered with correct fields + summary")
    void twoOpenLotsReturnedWithSummary() {
        FxPositionLot lot1 = FxPositionLot.acquire(TENANT, ACCOUNT, USD,
                T1, 1L, 1_000L, 1_300_000L, "entry-1", NOW);
        FxPositionLot lot2 = FxPositionLot.acquire(TENANT, ACCOUNT, USD,
                T2, 2L, 500L, 700_000L, "entry-2", NOW);

        when(fxPositionLotRepository.findOpenLots(TENANT, ACCOUNT, USD))
                .thenReturn(List.of(lot1, lot2));

        FxPositionLotsView view = useCase.get(TENANT, ACCOUNT, USD);

        assertThat(view.lotCount()).isEqualTo(2);
        assertThat(view.lots()).hasSize(2);

        // Lot 1 fields
        assertThat(view.lots().get(0).currency()).isEqualTo("USD");
        assertThat(view.lots().get(0).originalForeignMinor()).isEqualTo(1_000L);
        assertThat(view.lots().get(0).remainingForeignMinor()).isEqualTo(1_000L);
        assertThat(view.lots().get(0).originalBaseMinor()).isEqualTo(1_300_000L);
        assertThat(view.lots().get(0).carryingBaseMinor()).isEqualTo(1_300_000L);
        assertThat(view.lots().get(0).sourceJournalEntryId()).isEqualTo("entry-1");
        assertThat(view.lots().get(0).acquiredAt()).isEqualTo(T1);
        assertThat(view.lots().get(0).seq()).isEqualTo(1L);

        // Lot 2 fields
        assertThat(view.lots().get(1).originalForeignMinor()).isEqualTo(500L);
        assertThat(view.lots().get(1).carryingBaseMinor()).isEqualTo(700_000L);
        assertThat(view.lots().get(1).sourceJournalEntryId()).isEqualTo("entry-2");

        // Summary
        assertThat(view.totalRemainingForeignMinor()).isEqualTo(1_500L); // 1000 + 500
        assertThat(view.totalCarryingBaseMinor()).isEqualTo(2_000_000L); // 1_300_000 + 700_000
    }

    @Test
    @DisplayName("no open lots → empty list + zero summary (not 404)")
    void emptyPositionReturnsZeroSummary() {
        when(fxPositionLotRepository.findOpenLots(TENANT, ACCOUNT, USD))
                .thenReturn(List.of());

        FxPositionLotsView view = useCase.get(TENANT, ACCOUNT, USD);

        assertThat(view.lotCount()).isZero();
        assertThat(view.lots()).isEmpty();
        assertThat(view.totalRemainingForeignMinor()).isZero();
        assertThat(view.totalCarryingBaseMinor()).isZero();
    }

    @Test
    @DisplayName("partially consumed lot — remainingForeignMinor < originalForeignMinor")
    void partiallyConsumedLotIsReflected() {
        FxPositionLot lot = FxPositionLot.acquire(TENANT, ACCOUNT, USD,
                T1, 1L, 1_000L, 1_400_000L, "entry-3", NOW);
        lot.consume(600L, 840_000L);   // 600 of 1000 consumed → remaining 400, carrying 560_000

        when(fxPositionLotRepository.findOpenLots(TENANT, ACCOUNT, USD))
                .thenReturn(List.of(lot));

        FxPositionLotsView view = useCase.get(TENANT, ACCOUNT, USD);

        assertThat(view.lotCount()).isEqualTo(1);
        assertThat(view.lots().get(0).remainingForeignMinor()).isEqualTo(400L);
        assertThat(view.lots().get(0).carryingBaseMinor()).isEqualTo(560_000L);
        assertThat(view.lots().get(0).originalForeignMinor()).isEqualTo(1_000L);
        assertThat(view.totalRemainingForeignMinor()).isEqualTo(400L);
        assertThat(view.totalCarryingBaseMinor()).isEqualTo(560_000L);
    }
}
