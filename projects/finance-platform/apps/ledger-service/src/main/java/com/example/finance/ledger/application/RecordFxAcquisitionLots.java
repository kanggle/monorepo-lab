package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.account.NormalSide;
import com.example.finance.ledger.domain.journal.EntryDirection;
import com.example.finance.ledger.domain.journal.FxPositionLot;
import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.JournalLine;
import com.example.finance.ledger.domain.journal.repository.FxPositionLotRepository;
import com.example.finance.ledger.domain.money.LedgerReportingCurrency;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Acquisition-lot hook (16th increment — TASK-FIN-BE-024, architecture.md § FX
 * position lots, ADR-001 D2). For each line of a just-saved {@link JournalEntry}
 * that is a foreign-currency <b>acquisition</b>, persist one {@link FxPositionLot}.
 * Called from {@link PostJournalEntryUseCase#post} AFTER {@code save(entry)} and
 * INSIDE the same {@code @Transactional} boundary, so lot creation is atomic with
 * the entry (a guard-rejected posting threw earlier → no lots).
 *
 * <p><b>Acquisition predicate</b> — a line is an acquisition (creates exactly one
 * lot) iff ALL hold:
 * <ol>
 *   <li>{@code currency != } {@link LedgerReportingCurrency#BASE} (foreign — a KRW
 *       base-currency line has no FX exposure);</li>
 *   <li>{@code amountMinor > 0} (excludes the zero-amount {@code baseAdjustment}
 *       revaluation line — a revaluation does not buy currency);</li>
 *   <li>the line's {@code direction} is the account's <b>position-increasing</b>
 *       side ({@code direction == typeForCode(code).normalSide()}): a DEBIT on a
 *       DEBIT-normal account (ASSET/EXPENSE) or a CREDIT on a CREDIT-normal account
 *       (LIABILITY/INCOME/EQUITY).</li>
 * </ol>
 * A position-<b>reducing</b> foreign line (opposite side) creates NO lot — its
 * consumption is the FIFO settlement path (FIN-BE-025). This is the known shadow
 * desync for non-settlement reductions, documented in the architecture spec.
 *
 * <p>The account row is guaranteed to exist by the use case's lazy-create
 * ({@code ensureAccountExists}); the lot resolves the normal side from
 * {@link LedgerAccountCodes#typeForCode} — the same canonical mapping that
 * lazy-create stamps onto the account, so the two never diverge.
 */
@Component
@RequiredArgsConstructor
public class RecordFxAcquisitionLots {

    private final FxPositionLotRepository fxPositionLotRepository;
    private final ClockPort clock;

    /** Persist one acquisition lot per position-increasing foreign line of the saved entry. */
    public void record(JournalEntry saved) {
        Instant now = clock.now();
        for (JournalLine line : saved.lines()) {
            if (isAcquisition(line)) {
                fxPositionLotRepository.save(FxPositionLot.acquire(
                        saved.tenantId(), line.ledgerAccountCode(), line.currency(),
                        line.postedAt(), line.id(),
                        line.amountMinor(), line.baseAmountMinor(),
                        saved.entryId(), now));
            }
        }
    }

    /** The 3-clause acquisition predicate (see class javadoc). */
    boolean isAcquisition(JournalLine line) {
        if (line.currency() == LedgerReportingCurrency.BASE) {
            return false; // base-currency (KRW) line — no FX exposure
        }
        if (line.amountMinor() <= 0L) {
            return false; // zero-amount baseAdjustment revaluation line
        }
        NormalSide increasing =
                LedgerAccountCodes.typeForCode(line.ledgerAccountCode()).normalSide();
        return line.direction() == toDirection(increasing);
    }

    private static EntryDirection toDirection(NormalSide side) {
        return side == NormalSide.DEBIT ? EntryDirection.DEBIT : EntryDirection.CREDIT;
    }
}
