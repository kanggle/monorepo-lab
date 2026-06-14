package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.journal.EntryDirection;
import com.example.finance.ledger.domain.journal.JournalLine;
import com.example.finance.ledger.domain.journal.repository.FxPositionLotRepository;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The 3-clause FX acquisition predicate (16th increment — TASK-FIN-BE-024, ADR-001
 * D2). A line creates a lot iff it is foreign (currency != KRW), positive-amount
 * (excludes the zero-amount revaluation adjustment), and posts on the account's
 * position-INCREASING side. KRW lines, zero-amount lines, and opposite-side foreign
 * lines create no lot (shadow — non-settlement reductions are FIN-BE-025).
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class RecordFxAcquisitionLotsTest {

    private static final String TENANT = "finance";
    // CASH_CLEARING resolves to ASSET (normal DEBIT-side increasing).
    private static final String ASSET_CODE = LedgerAccountCodes.CASH_CLEARING;
    // A customer wallet resolves to LIABILITY (normal CREDIT-side increasing).
    private static final String LIABILITY_CODE = LedgerAccountCodes.customerWallet("acc-1");

    @Mock FxPositionLotRepository fxPositionLotRepository;
    @Mock ClockPort clock;

    @InjectMocks RecordFxAcquisitionLots component;

    private static JournalLine foreign(String code, EntryDirection direction,
                                       long foreignMinor, long baseMinor) {
        return JournalLine.of(TENANT, code, direction,
                Money.of(foreignMinor, Currency.USD), Money.of(baseMinor, Currency.KRW));
    }

    @Test
    @DisplayName("foreign + positive + position-increasing side (DEBIT on ASSET) → acquisition")
    void foreignPositiveIncreasingIsAcquisition() {
        JournalLine line = foreign(ASSET_CODE, EntryDirection.DEBIT, 10_000L, 130_000L);
        assertThat(component.isAcquisition(line)).isTrue();
    }

    @Test
    @DisplayName("foreign + positive + position-increasing side (CREDIT on LIABILITY) → acquisition")
    void creditIncreasingLiabilityIsAcquisition() {
        JournalLine line = foreign(LIABILITY_CODE, EntryDirection.CREDIT, 10_000L, 130_000L);
        assertThat(component.isAcquisition(line)).isTrue();
    }

    @Test
    @DisplayName("KRW (base-currency) line → not an acquisition")
    void krwLineIsNotAcquisition() {
        JournalLine line = JournalLine.debit(TENANT, ASSET_CODE, Money.of(130_000L, Currency.KRW));
        assertThat(component.isAcquisition(line)).isFalse();
    }

    @Test
    @DisplayName("zero-amount baseAdjustment (revaluation) foreign line → not an acquisition")
    void zeroAmountRevaluationLineIsNotAcquisition() {
        JournalLine line = JournalLine.baseAdjustment(TENANT, ASSET_CODE, Currency.USD,
                EntryDirection.DEBIT, Money.of(5_000L, Currency.KRW), new BigDecimal("13.5"));
        assertThat(component.isAcquisition(line)).isFalse();
    }

    @Test
    @DisplayName("position-REDUCING foreign line (CREDIT on ASSET) → not an acquisition")
    void positionReducingForeignLineIsNotAcquisition() {
        JournalLine line = foreign(ASSET_CODE, EntryDirection.CREDIT, 10_000L, 130_000L);
        assertThat(component.isAcquisition(line)).isFalse();
    }
}
