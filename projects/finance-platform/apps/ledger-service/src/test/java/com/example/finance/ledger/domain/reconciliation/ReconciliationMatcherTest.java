package com.example.finance.ledger.domain.reconciliation;

import com.example.finance.ledger.domain.account.LedgerAccountCodes;
import com.example.finance.ledger.domain.journal.EntryDirection;
import com.example.finance.ledger.domain.money.Currency;
import com.example.finance.ledger.domain.money.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exhaustive unit tests for the pure {@link ReconciliationMatcher} (AC-1; F8 — the
 * matcher only RECORDS, never resolves). 1:1 by (amount, currency, direction);
 * unmatched-external → UNMATCHED_EXTERNAL; unmatched-internal → UNMATCHED_INTERNAL;
 * direction discriminates; multi-line determinism. No Spring/JPA.
 *
 * <p>The pre-13th-increment cases pass {@link FxTolerance#EXACT} so the base-leg
 * compare is byte-identical to FIN-BE-017 (net-zero). The 13th-increment block
 * (TASK-FIN-BE-020) exercises a non-zero tolerance: within → match, no discrepancy;
 * at-edge → within; above → AMOUNT_MISMATCH; looser-of bps-vs-floor.
 */
class ReconciliationMatcherTest {

    private static final String TENANT = "finance";
    private static final String STATEMENT = "stmt-1";
    private static final String CODE = LedgerAccountCodes.CASH_CLEARING;
    private static final Instant AT = Instant.parse("2026-01-31T00:00:00Z");
    private static final LocalDate VALUE_DATE = LocalDate.parse("2026-01-15");
    /** The net-zero default used by the FIN-BE-010/017 cases (exact compare). */
    private static final FxTolerance EXACT = FxTolerance.EXACT;

    private static Money krw(long m) {
        return Money.of(m, Currency.KRW);
    }

    private static Money usd(long m) {
        return Money.of(m, Currency.USD);
    }

    private static ExternalStatementLine ext(String ref, long amount, EntryDirection dir) {
        return ExternalStatementLine.of(null, STATEMENT, TENANT, ref, krw(amount), dir,
                VALUE_DATE, null);
    }

    /** A foreign (USD) external line with an optional declared base (KRW) value. */
    private static ExternalStatementLine extUsd(String ref, long usdAmount, EntryDirection dir,
                                                Long baseKrw) {
        return ExternalStatementLine.of(null, STATEMENT, TENANT, ref, usd(usdAmount), dir,
                VALUE_DATE, null, baseKrw == null ? null : krw(baseKrw));
    }

    /** A KRW internal line — base == amount. */
    private static InternalLine internal(String entryId, long amount, EntryDirection dir) {
        return new InternalLine(entryId, CODE, dir, krw(amount), krw(amount));
    }

    /** A foreign (USD) internal line carrying a KRW base (the booked carrying value). */
    private static InternalLine internalUsd(String entryId, long usdAmount, EntryDirection dir,
                                            long baseKrw) {
        return new InternalLine(entryId, CODE, dir, usd(usdAmount), krw(baseKrw));
    }

    @Test
    @DisplayName("1:1 — all external lines match an internal line, zero discrepancies")
    void allMatched() {
        ExternalStatementLine e1 = ext("R1", 150_000, EntryDirection.DEBIT);
        ExternalStatementLine e2 = ext("R2", 70_000, EntryDirection.DEBIT);
        List<InternalLine> internals = List.of(
                internal("entry-a", 150_000, EntryDirection.DEBIT),
                internal("entry-b", 70_000, EntryDirection.DEBIT));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1, e2), internals, EXACT, AT);

        assertThat(result.matchedCount()).isEqualTo(2);
        assertThat(result.discrepancyCount()).isZero();
        assertThat(e1.isMatched()).isTrue();
        assertThat(e2.isMatched()).isTrue();
        assertThat(result.matches()).extracting(ReconciliationMatch::journalEntryId)
                .containsExactlyInAnyOrder("entry-a", "entry-b");
    }

    @Test
    @DisplayName("an external line with no internal counterpart → UNMATCHED_EXTERNAL (expected=ext, actual=0)")
    void unmatchedExternal() {
        ExternalStatementLine e1 = ext("R1", 150_000, EntryDirection.DEBIT);
        ExternalStatementLine e2 = ext("R2", 99_000, EntryDirection.DEBIT);
        List<InternalLine> internals = List.of(
                internal("entry-a", 150_000, EntryDirection.DEBIT));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1, e2), internals, EXACT, AT);

        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.discrepancyCount()).isEqualTo(1);
        assertThat(e2.isMatched()).isFalse();
        ReconciliationDiscrepancy d = result.discrepancies().get(0);
        assertThat(d.type()).isEqualTo(DiscrepancyType.UNMATCHED_EXTERNAL);
        assertThat(d.externalRef()).isEqualTo("R2");
        assertThat(d.journalEntryId()).isNull();
        assertThat(d.expectedMinor()).isEqualTo(99_000L);
        assertThat(d.actualMinor()).isZero();
        assertThat(d.status()).isEqualTo(DiscrepancyStatus.OPEN);
    }

    @Test
    @DisplayName("an internal line with no external counterpart → UNMATCHED_INTERNAL (expected=0, actual=internal)")
    void unmatchedInternal() {
        ExternalStatementLine e1 = ext("R1", 150_000, EntryDirection.DEBIT);
        List<InternalLine> internals = List.of(
                internal("entry-a", 150_000, EntryDirection.DEBIT),
                internal("entry-b", 88_000, EntryDirection.DEBIT));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, EXACT, AT);

        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.discrepancyCount()).isEqualTo(1);
        ReconciliationDiscrepancy d = result.discrepancies().get(0);
        assertThat(d.type()).isEqualTo(DiscrepancyType.UNMATCHED_INTERNAL);
        assertThat(d.journalEntryId()).isEqualTo("entry-b");
        assertThat(d.externalRef()).isNull();
        assertThat(d.expectedMinor()).isZero();
        assertThat(d.actualMinor()).isEqualTo(88_000L);
        assertThat(d.status()).isEqualTo(DiscrepancyStatus.OPEN);
    }

    @Test
    @DisplayName("mixed — one match + one UNMATCHED_EXTERNAL + one UNMATCHED_INTERNAL")
    void mixed() {
        ExternalStatementLine e1 = ext("R1", 150_000, EntryDirection.DEBIT);
        ExternalStatementLine e2 = ext("R2", 70_000, EntryDirection.DEBIT);  // no internal
        List<InternalLine> internals = List.of(
                internal("entry-a", 150_000, EntryDirection.DEBIT),
                internal("entry-b", 99_000, EntryDirection.DEBIT));            // no external

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1, e2), internals, EXACT, AT);

        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.discrepancyCount()).isEqualTo(2);
        assertThat(result.discrepancies()).extracting(ReconciliationDiscrepancy::type)
                .containsExactlyInAnyOrder(
                        DiscrepancyType.UNMATCHED_EXTERNAL, DiscrepancyType.UNMATCHED_INTERNAL);
        assertThat(result.discrepancies()).allMatch(ReconciliationDiscrepancy::isOpen);
    }

    @Test
    @DisplayName("direction discriminates — same amount, opposite direction → no match")
    void directionDiscriminates() {
        ExternalStatementLine e1 = ext("R1", 150_000, EntryDirection.DEBIT);
        List<InternalLine> internals = List.of(
                internal("entry-a", 150_000, EntryDirection.CREDIT));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, EXACT, AT);

        assertThat(result.matchedCount()).isZero();
        assertThat(result.discrepancyCount()).isEqualTo(2);
        assertThat(result.discrepancies()).extracting(ReconciliationDiscrepancy::type)
                .containsExactlyInAnyOrder(
                        DiscrepancyType.UNMATCHED_EXTERNAL, DiscrepancyType.UNMATCHED_INTERNAL);
    }

    @Test
    @DisplayName("multi-line determinism — two equal external + two equal internal → both match")
    void multiLineDeterminism() {
        ExternalStatementLine e1 = ext("R1", 50_000, EntryDirection.DEBIT);
        ExternalStatementLine e2 = ext("R2", 50_000, EntryDirection.DEBIT);
        List<InternalLine> internals = List.of(
                internal("entry-a", 50_000, EntryDirection.DEBIT),
                internal("entry-b", 50_000, EntryDirection.DEBIT));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1, e2), internals, EXACT, AT);

        assertThat(result.matchedCount()).isEqualTo(2);
        assertThat(result.discrepancyCount()).isZero();
        // Deterministic: e1 consumes the first candidate (entry-a), e2 the next (entry-b).
        assertThat(result.matches().get(0).statementLineId()).isEqualTo(e1.lineId());
        assertThat(result.matches().get(0).journalEntryId()).isEqualTo("entry-a");
        assertThat(result.matches().get(1).statementLineId()).isEqualTo(e2.lineId());
        assertThat(result.matches().get(1).journalEntryId()).isEqualTo("entry-b");
    }

    @Test
    @DisplayName("empty statement, no internal lines → zero matches, zero discrepancies")
    void emptyStatement() {
        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(), List.of(), EXACT, AT);

        assertThat(result.matchedCount()).isZero();
        assertThat(result.discrepancyCount()).isZero();
    }

    // ---- 11th increment: base (FX) leg (TASK-FIN-BE-017, multi-currency) ----

    @Test
    @DisplayName("(AC-1) foreign line matches on txn leg but base differs → MATCHED + AMOUNT_MISMATCH "
            + "(expected=internal base, actual=external base, currency=KRW, both refs set)")
    void foreignBaseDiffersRecordsAmountMismatch() {
        // USD 10000 DEBIT, internal carrying base 130000 KRW; bank reports 132000 KRW.
        ExternalStatementLine e1 = extUsd("FX1", 10_000, EntryDirection.DEBIT, 132_000L);
        List<InternalLine> internals = List.of(
                internalUsd("entry-fx", 10_000, EntryDirection.DEBIT, 130_000L));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, EXACT, AT);

        // Transaction leg STILL reconciled — the line is MATCHED and a match exists.
        assertThat(e1.isMatched()).isTrue();
        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.matches().get(0).journalEntryId()).isEqualTo("entry-fx");

        // Base (FX) leg — exactly one AMOUNT_MISMATCH, OPEN, carrying both refs.
        assertThat(result.discrepancyCount()).isEqualTo(1);
        ReconciliationDiscrepancy d = result.discrepancies().get(0);
        assertThat(d.type()).isEqualTo(DiscrepancyType.AMOUNT_MISMATCH);
        assertThat(d.externalRef()).isEqualTo("FX1");
        assertThat(d.journalEntryId()).isEqualTo("entry-fx");
        assertThat(d.expectedMinor()).isEqualTo(130_000L); // internal carrying base
        assertThat(d.actualMinor()).isEqualTo(132_000L);   // bank-reported base
        assertThat(d.currency()).isEqualTo(Currency.KRW);
        assertThat(d.status()).isEqualTo(DiscrepancyStatus.OPEN);
    }

    @Test
    @DisplayName("(AC-2) foreign line whose external base EQUALS the internal base → MATCHED, no discrepancy")
    void foreignBaseEqualNoDiscrepancy() {
        ExternalStatementLine e1 = extUsd("FX1", 10_000, EntryDirection.DEBIT, 130_000L);
        List<InternalLine> internals = List.of(
                internalUsd("entry-fx", 10_000, EntryDirection.DEBIT, 130_000L));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, EXACT, AT);

        assertThat(e1.isMatched()).isTrue();
        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.discrepancyCount()).isZero();
    }

    @Test
    @DisplayName("(AC-2) KRW line (base == amount) → MATCHED, no base-leg discrepancy")
    void krwLineNoBaseLegDiscrepancy() {
        ExternalStatementLine e1 = ext("R1", 150_000, EntryDirection.DEBIT);
        List<InternalLine> internals = List.of(
                internal("entry-a", 150_000, EntryDirection.DEBIT));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, EXACT, AT);

        assertThat(e1.isMatched()).isTrue();
        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.discrepancyCount()).isZero();
    }

    @Test
    @DisplayName("(AC-2) foreign line WITHOUT a declared external base → MATCHED, no base-leg discrepancy")
    void foreignLineWithoutBaseNoDiscrepancy() {
        ExternalStatementLine e1 = extUsd("FX1", 10_000, EntryDirection.DEBIT, null);
        List<InternalLine> internals = List.of(
                internalUsd("entry-fx", 10_000, EntryDirection.DEBIT, 130_000L));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, EXACT, AT);

        assertThat(e1.isMatched()).isTrue();
        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.discrepancyCount()).isZero();
    }

    // ---- 13th increment: configurable base-leg FX tolerance (TASK-FIN-BE-020) ----

    /** Runs the matcher for one USD line (base diff) under {@code tolerance}. */
    private static ReconciliationResult matchUsd(long internalBase, Long externalBase,
                                                 FxTolerance tolerance) {
        ExternalStatementLine e1 = extUsd("FX1", 10_000, EntryDirection.DEBIT, externalBase);
        List<InternalLine> internals = List.of(
                internalUsd("entry-fx", 10_000, EntryDirection.DEBIT, internalBase));
        return ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, tolerance, AT);
    }

    @Test
    @DisplayName("(AC-2) EXACT reproduces FIN-BE-017 — any non-zero base diff → AMOUNT_MISMATCH")
    void exactReproducesFinBe017() {
        // 1 minor unit of difference under EXACT still raises AMOUNT_MISMATCH (net-zero
        // iff expected == actual; the band is max(0,0) == 0).
        ReconciliationResult result = matchUsd(130_000L, 130_001L, FxTolerance.EXACT);

        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.discrepancyCount()).isEqualTo(1);
        assertThat(result.discrepancies().get(0).type()).isEqualTo(DiscrepancyType.AMOUNT_MISMATCH);
        assertThat(result.discrepancies().get(0).expectedMinor()).isEqualTo(130_000L);
        assertThat(result.discrepancies().get(0).actualMinor()).isEqualTo(130_001L);
    }

    @Test
    @DisplayName("(AC-2) EXACT with equal base → MATCHED, no discrepancy (net-zero)")
    void exactEqualBaseNoDiscrepancy() {
        ReconciliationResult result = matchUsd(130_000L, 130_000L, FxTolerance.EXACT);
        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.discrepancyCount()).isZero();
    }

    @Test
    @DisplayName("base diff WITHIN the bps band → MATCHED, no discrepancy (txn match still recorded)")
    void withinBpsBandSuppressesDiscrepancy() {
        // 100 bps of 130000 = 1300 band. A 1200 diff (132000-130800... use 130000 vs 131200) is within.
        FxTolerance tol = new FxTolerance(100, 0L); // 1% band → 1300 on 130000
        ReconciliationResult result = matchUsd(130_000L, 131_200L, tol); // diff 1200 <= 1300

        // F8 — the transaction-leg match is STILL recorded; tolerance suppresses only the base discrepancy.
        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.matches().get(0).journalEntryId()).isEqualTo("entry-fx");
        assertThat(result.discrepancyCount()).isZero();
    }

    @Test
    @DisplayName("base diff EXACTLY at the bps band edge → WITHIN (<=, inclusive) → no discrepancy")
    void atBandEdgeIsWithin() {
        FxTolerance tol = new FxTolerance(100, 0L); // 1% of 130000 = 1300 exactly
        ReconciliationResult result = matchUsd(130_000L, 131_300L, tol); // diff == 1300 == band

        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.discrepancyCount()).isZero();
    }

    @Test
    @DisplayName("base diff ABOVE the bps band → AMOUNT_MISMATCH (expected/actual/currency as FIN-BE-017)")
    void aboveBandRecordsAmountMismatch() {
        FxTolerance tol = new FxTolerance(100, 0L); // band 1300 on 130000
        ReconciliationResult result = matchUsd(130_000L, 131_301L, tol); // diff 1301 > 1300

        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.discrepancyCount()).isEqualTo(1);
        ReconciliationDiscrepancy d = result.discrepancies().get(0);
        assertThat(d.type()).isEqualTo(DiscrepancyType.AMOUNT_MISMATCH);
        assertThat(d.externalRef()).isEqualTo("FX1");
        assertThat(d.journalEntryId()).isEqualTo("entry-fx");
        assertThat(d.expectedMinor()).isEqualTo(130_000L);
        assertThat(d.actualMinor()).isEqualTo(131_301L);
        assertThat(d.currency()).isEqualTo(Currency.KRW);
        assertThat(d.status()).isEqualTo(DiscrepancyStatus.OPEN);
    }

    @Test
    @DisplayName("looser-of — small amount where the floor exceeds the bps band → floor wins (within)")
    void floorWinsOnSmallAmount() {
        // 100 bps of 1000 = 10 (bps band); floor = 50. A 40 diff is above the bps band
        // but within the floor → looser (floor=50) wins → no discrepancy.
        FxTolerance tol = new FxTolerance(100, 50L);
        ExternalStatementLine e1 = extUsd("FX1", 100, EntryDirection.DEBIT, 1_040L);
        List<InternalLine> internals = List.of(
                internalUsd("entry-fx", 100, EntryDirection.DEBIT, 1_000L)); // diff 40
        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, tol, AT);

        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.discrepancyCount()).isZero(); // 40 <= max(50, 10) = 50
    }

    @Test
    @DisplayName("looser-of — large amount where the bps band exceeds the floor → bps wins (within)")
    void bpsWinsOnLargeAmount() {
        // 100 bps of 1000000 = 10000 (bps band); floor = 50. A 9000 diff is within the
        // bps band though far above the floor → looser (bps=10000) wins → no discrepancy.
        FxTolerance tol = new FxTolerance(100, 50L);
        ReconciliationResult result = matchUsd(1_000_000L, 1_009_000L, tol); // diff 9000 <= 10000

        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.discrepancyCount()).isZero();
    }

    @Test
    @DisplayName("tolerance never fires on a KRW line regardless of band (base-leg only)")
    void krwLineUnaffectedByTolerance() {
        FxTolerance tol = new FxTolerance(100, 1_000L);
        ExternalStatementLine e1 = ext("R1", 150_000, EntryDirection.DEBIT);
        List<InternalLine> internals = List.of(
                internal("entry-a", 150_000, EntryDirection.DEBIT));
        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, tol, AT);

        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.discrepancyCount()).isZero();
    }

    @Test
    @DisplayName("tolerance never fires on a base-less foreign line regardless of band")
    void baseLessLineUnaffectedByTolerance() {
        FxTolerance tol = new FxTolerance(100, 1_000L);
        ReconciliationResult result = matchUsd(130_000L, null, tol);

        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.discrepancyCount()).isZero();
    }

    // ---- 14th increment: cross-currency base-leg matching (TASK-FIN-BE-021) ----
    // A base-currency (KRW) external line with NO same-currency candidate falls back to
    // a FOREIGN internal line whose carrying base (baseMoney) is within tolerance of the
    // external KRW amount. Same-currency matching takes precedence; the base comparison
    // is the match key (no AMOUNT_MISMATCH); beyond tolerance → UNMATCHED_EXTERNAL.

    @Test
    @DisplayName("(AC-1) KRW external, no KRW candidate but a carrying-base-equal foreign internal "
            + "→ cross-currency match (crossCurrency=true, NO discrepancy), both consumed")
    void crossCurrencyMatchOnEqualCarryingBase() {
        // No KRW internal; one USD internal carrying 130000 KRW. KRW external = 130000.
        ExternalStatementLine e1 = ext("KRWEXT", 130_000, EntryDirection.DEBIT);
        List<InternalLine> internals = List.of(
                internalUsd("entry-fx", 10_000, EntryDirection.DEBIT, 130_000L));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, EXACT, AT);

        assertThat(e1.isMatched()).isTrue();
        assertThat(result.matchedCount()).isEqualTo(1);
        // No AMOUNT_MISMATCH / UNMATCHED — the base comparison IS the match key.
        assertThat(result.discrepancyCount()).isZero();
        ReconciliationMatch m = result.matches().get(0);
        assertThat(m.journalEntryId()).isEqualTo("entry-fx");
        assertThat(m.crossCurrency()).isTrue();
        // The match carries the external KRW money (not the internal USD amount).
        assertThat(m.money()).isEqualTo(krw(130_000));
    }

    @Test
    @DisplayName("(precedence) a KRW external with BOTH a KRW internal and a carrying-base foreign "
            + "internal → the same-currency KRW match wins (crossCurrency=false); foreign stays")
    void sameCurrencyTakesPrecedenceOverCrossCurrency() {
        ExternalStatementLine e1 = ext("KRWEXT", 130_000, EntryDirection.DEBIT);
        // Foreign internal first in input order, KRW internal second — the same-currency
        // pass must still win regardless of input order (it runs FIRST, before fallback).
        List<InternalLine> internals = List.of(
                internalUsd("entry-fx", 10_000, EntryDirection.DEBIT, 130_000L),
                internal("entry-krw", 130_000, EntryDirection.DEBIT));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, EXACT, AT);

        assertThat(e1.isMatched()).isTrue();
        assertThat(result.matchedCount()).isEqualTo(1);
        ReconciliationMatch m = result.matches().get(0);
        assertThat(m.journalEntryId()).isEqualTo("entry-krw"); // same-currency wins
        assertThat(m.crossCurrency()).isFalse();
        // The foreign internal is NOT consumed by the KRW external → it stays unmatched.
        assertThat(result.discrepancyCount()).isEqualTo(1);
        ReconciliationDiscrepancy d = result.discrepancies().get(0);
        assertThat(d.type()).isEqualTo(DiscrepancyType.UNMATCHED_INTERNAL);
        assertThat(d.journalEntryId()).isEqualTo("entry-fx");
    }

    @Test
    @DisplayName("(AC-2) KRW external WITHIN a configured tolerance of the foreign carrying base "
            + "→ cross-currency match, no discrepancy")
    void crossCurrencyMatchWithinTolerance() {
        FxTolerance tol = new FxTolerance(100, 0L); // 1% of 130000 = 1300 band
        ExternalStatementLine e1 = ext("KRWEXT", 131_200, EntryDirection.DEBIT); // diff 1200 <= 1300
        List<InternalLine> internals = List.of(
                internalUsd("entry-fx", 10_000, EntryDirection.DEBIT, 130_000L));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, tol, AT);

        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.discrepancyCount()).isZero();
        assertThat(result.matches().get(0).crossCurrency()).isTrue();
        assertThat(result.matches().get(0).money()).isEqualTo(krw(131_200));
    }

    @Test
    @DisplayName("(AC-2) KRW external BEYOND tolerance of the foreign carrying base → NO cross match "
            + "→ UNMATCHED_EXTERNAL + the foreign internal → UNMATCHED_INTERNAL (as today)")
    void crossCurrencyBeyondToleranceFallsThrough() {
        FxTolerance tol = new FxTolerance(100, 0L); // band 1300 on 130000
        ExternalStatementLine e1 = ext("KRWEXT", 131_301, EntryDirection.DEBIT); // diff 1301 > 1300
        List<InternalLine> internals = List.of(
                internalUsd("entry-fx", 10_000, EntryDirection.DEBIT, 130_000L));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, tol, AT);

        assertThat(e1.isMatched()).isFalse();
        assertThat(result.matchedCount()).isZero();
        assertThat(result.discrepancyCount()).isEqualTo(2);
        assertThat(result.discrepancies()).extracting(ReconciliationDiscrepancy::type)
                .containsExactlyInAnyOrder(
                        DiscrepancyType.UNMATCHED_EXTERNAL, DiscrepancyType.UNMATCHED_INTERNAL);
    }

    @Test
    @DisplayName("(EXACT default) cross-currency requires EXACT carrying-base equality — 1 unit off "
            + "→ NO cross match → UNMATCHED_EXTERNAL + UNMATCHED_INTERNAL")
    void crossCurrencyExactRequiresEquality() {
        ExternalStatementLine e1 = ext("KRWEXT", 130_001, EntryDirection.DEBIT); // 1 off
        List<InternalLine> internals = List.of(
                internalUsd("entry-fx", 10_000, EntryDirection.DEBIT, 130_000L));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, EXACT, AT);

        assertThat(result.matchedCount()).isZero();
        assertThat(result.discrepancyCount()).isEqualTo(2);
        assertThat(result.discrepancies()).extracting(ReconciliationDiscrepancy::type)
                .containsExactlyInAnyOrder(
                        DiscrepancyType.UNMATCHED_EXTERNAL, DiscrepancyType.UNMATCHED_INTERNAL);
    }

    @Test
    @DisplayName("a FOREIGN external line never enters the cross-currency pass (direction is "
            + "base-external → foreign-internal only)")
    void foreignExternalNeverTriggersCrossCurrency() {
        // A USD external (10000) with NO same-currency USD internal; a USD internal exists
        // but at a different USD amount (so findCandidate misses), whose carrying base
        // (10000 KRW) equals the USD external's USD amount numerically — must NOT match.
        ExternalStatementLine e1 = extUsd("FXEXT", 10_000, EntryDirection.DEBIT, null);
        List<InternalLine> internals = List.of(
                internalUsd("entry-fx", 9_999, EntryDirection.DEBIT, 10_000L));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, EXACT, AT);

        // A foreign external never falls back → UNMATCHED_EXTERNAL + UNMATCHED_INTERNAL.
        assertThat(result.matchedCount()).isZero();
        assertThat(result.discrepancyCount()).isEqualTo(2);
        assertThat(result.discrepancies()).extracting(ReconciliationDiscrepancy::type)
                .containsExactlyInAnyOrder(
                        DiscrepancyType.UNMATCHED_EXTERNAL, DiscrepancyType.UNMATCHED_INTERNAL);
    }

    @Test
    @DisplayName("a KRW external never cross-matches a KRW internal (cross pass considers "
            + "currency != BASE only) — a non-equal KRW internal stays unmatched")
    void krwExternalDoesNotCrossMatchKrwInternal() {
        // KRW external 130000; a KRW internal carries base 130000 but its amount is 999
        // (so findCandidate misses on amount). The cross pass must SKIP it (currency==BASE)
        // → no false cross-currency match.
        ExternalStatementLine e1 = ext("KRWEXT", 130_000, EntryDirection.DEBIT);
        List<InternalLine> internals = List.of(
                internal("entry-krw", 999, EntryDirection.DEBIT)); // base==amount==999

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, EXACT, AT);

        assertThat(result.matchedCount()).isZero();
        assertThat(result.discrepancyCount()).isEqualTo(2);
        assertThat(result.discrepancies()).extracting(ReconciliationDiscrepancy::type)
                .containsExactlyInAnyOrder(
                        DiscrepancyType.UNMATCHED_EXTERNAL, DiscrepancyType.UNMATCHED_INTERNAL);
    }

    @Test
    @DisplayName("cross-currency determinism — first not-consumed foreign internal by input order")
    void crossCurrencyDeterministicFirstNotConsumed() {
        // Two KRW externals each 130000; two foreign internals each carrying 130000.
        ExternalStatementLine e1 = ext("KRWEXT-1", 130_000, EntryDirection.DEBIT);
        ExternalStatementLine e2 = ext("KRWEXT-2", 130_000, EntryDirection.DEBIT);
        List<InternalLine> internals = List.of(
                internalUsd("entry-fx-a", 10_000, EntryDirection.DEBIT, 130_000L),
                internalUsd("entry-fx-b", 11_000, EntryDirection.DEBIT, 130_000L));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1, e2), internals, EXACT, AT);

        assertThat(result.matchedCount()).isEqualTo(2);
        assertThat(result.discrepancyCount()).isZero();
        // e1 consumes the first foreign internal, e2 the next (deterministic, input order).
        assertThat(result.matches().get(0).journalEntryId()).isEqualTo("entry-fx-a");
        assertThat(result.matches().get(1).journalEntryId()).isEqualTo("entry-fx-b");
        assertThat(result.matches()).allMatch(ReconciliationMatch::crossCurrency);
    }

    // ---- 19th increment: reverse cross-currency matching (TASK-FIN-BE-027) ----
    // The MIRROR of the 14th increment. A FOREIGN external line (currency != KRW) carrying a
    // declared baseAmount, with NO same-currency candidate, falls back to a BASE-currency (KRW)
    // internal line whose KRW amount (money) is within tolerance of the external's baseAmount.
    // Same-currency matching takes precedence; the base comparison is the match key (no
    // AMOUNT_MISMATCH); a foreign external without a baseAmount, or beyond tolerance →
    // UNMATCHED_EXTERNAL. The two cross passes are mutually exclusive (KRW external vs foreign).

    @Test
    @DisplayName("(AC-1) foreign external (USD, baseAmount 130000) + a KRW internal (money 130000, "
            + "same direction) → reverse cross match (crossCurrency=true, NO discrepancy), both consumed")
    void reverseCrossCurrencyMatchOnEqualBase() {
        // No USD internal; one KRW internal 130000. USD external 10000 carrying base 130000 KRW.
        ExternalStatementLine e1 = extUsd("FXEXT", 10_000, EntryDirection.DEBIT, 130_000L);
        List<InternalLine> internals = List.of(
                internal("entry-krw", 130_000, EntryDirection.DEBIT));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, EXACT, AT);

        assertThat(e1.isMatched()).isTrue();
        assertThat(result.matchedCount()).isEqualTo(1);
        // No AMOUNT_MISMATCH / UNMATCHED — the base comparison IS the match key.
        assertThat(result.discrepancyCount()).isZero();
        ReconciliationMatch m = result.matches().get(0);
        assertThat(m.journalEntryId()).isEqualTo("entry-krw");
        assertThat(m.crossCurrency()).isTrue();
        // The match carries the external USD money (not the internal KRW amount).
        assertThat(m.money()).isEqualTo(usd(10_000));
    }

    @Test
    @DisplayName("(AC-2) foreign external WITHIN a configured tolerance of the KRW internal amount "
            + "→ reverse cross match, no discrepancy")
    void reverseCrossCurrencyMatchWithinTolerance() {
        FxTolerance tol = new FxTolerance(100, 0L); // 1% of 130000 = 1300 band
        // External base 131200 vs KRW internal 130000 → diff 1200 <= 1300 → within.
        ExternalStatementLine e1 = extUsd("FXEXT", 10_000, EntryDirection.DEBIT, 131_200L);
        List<InternalLine> internals = List.of(
                internal("entry-krw", 130_000, EntryDirection.DEBIT));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, tol, AT);

        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.discrepancyCount()).isZero();
        assertThat(result.matches().get(0).crossCurrency()).isTrue();
        assertThat(result.matches().get(0).money()).isEqualTo(usd(10_000));
    }

    @Test
    @DisplayName("(AC-2) foreign external BEYOND tolerance of the KRW internal amount → NO reverse "
            + "match → UNMATCHED_EXTERNAL + the KRW internal → UNMATCHED_INTERNAL")
    void reverseCrossCurrencyBeyondToleranceFallsThrough() {
        FxTolerance tol = new FxTolerance(100, 0L); // band 1300 on 130000
        // External base 131301 vs KRW internal 130000 → diff 1301 > 1300 → beyond.
        ExternalStatementLine e1 = extUsd("FXEXT", 10_000, EntryDirection.DEBIT, 131_301L);
        List<InternalLine> internals = List.of(
                internal("entry-krw", 130_000, EntryDirection.DEBIT));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, tol, AT);

        assertThat(e1.isMatched()).isFalse();
        assertThat(result.matchedCount()).isZero();
        assertThat(result.discrepancyCount()).isEqualTo(2);
        assertThat(result.discrepancies()).extracting(ReconciliationDiscrepancy::type)
                .containsExactlyInAnyOrder(
                        DiscrepancyType.UNMATCHED_EXTERNAL, DiscrepancyType.UNMATCHED_INTERNAL);
    }

    @Test
    @DisplayName("(EXACT default) reverse cross requires EXACT KRW-amount equality — 1 unit off "
            + "→ NO reverse match → UNMATCHED_EXTERNAL + UNMATCHED_INTERNAL")
    void reverseCrossCurrencyExactRequiresEquality() {
        ExternalStatementLine e1 = extUsd("FXEXT", 10_000, EntryDirection.DEBIT, 130_001L); // 1 off
        List<InternalLine> internals = List.of(
                internal("entry-krw", 130_000, EntryDirection.DEBIT));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, EXACT, AT);

        assertThat(result.matchedCount()).isZero();
        assertThat(result.discrepancyCount()).isEqualTo(2);
        assertThat(result.discrepancies()).extracting(ReconciliationDiscrepancy::type)
                .containsExactlyInAnyOrder(
                        DiscrepancyType.UNMATCHED_EXTERNAL, DiscrepancyType.UNMATCHED_INTERNAL);
    }

    @Test
    @DisplayName("(AC-3) a foreign external WITHOUT a declared baseAmount never enters the reverse "
            + "pass (no match key) → UNMATCHED_EXTERNAL + the KRW internal → UNMATCHED_INTERNAL")
    void reverseCrossCurrencyRequiresBaseAmount() {
        // USD external 10000, NO baseAmount; a KRW internal whose amount (10000) equals the
        // USD external's USD amount numerically — must NOT match (no base key).
        ExternalStatementLine e1 = extUsd("FXEXT", 10_000, EntryDirection.DEBIT, null);
        List<InternalLine> internals = List.of(
                internal("entry-krw", 10_000, EntryDirection.DEBIT));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, EXACT, AT);

        assertThat(e1.isMatched()).isFalse();
        assertThat(result.matchedCount()).isZero();
        assertThat(result.discrepancyCount()).isEqualTo(2);
        assertThat(result.discrepancies()).extracting(ReconciliationDiscrepancy::type)
                .containsExactlyInAnyOrder(
                        DiscrepancyType.UNMATCHED_EXTERNAL, DiscrepancyType.UNMATCHED_INTERNAL);
    }

    @Test
    @DisplayName("(precedence) a foreign external with BOTH a same-currency USD internal and a "
            + "base-matching KRW internal → the same-currency USD match wins (crossCurrency=false)")
    void sameCurrencyTakesPrecedenceOverReverseCrossCurrency() {
        // USD external 10000 carrying base 130000. A KRW internal (130000) would reverse-match,
        // but a same-currency USD internal (10000) exists → same-currency pass must win first.
        ExternalStatementLine e1 = extUsd("FXEXT", 10_000, EntryDirection.DEBIT, 130_000L);
        List<InternalLine> internals = List.of(
                internal("entry-krw", 130_000, EntryDirection.DEBIT),
                internalUsd("entry-usd", 10_000, EntryDirection.DEBIT, 130_000L));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, EXACT, AT);

        assertThat(e1.isMatched()).isTrue();
        assertThat(result.matchedCount()).isEqualTo(1);
        ReconciliationMatch m = result.matches().get(0);
        assertThat(m.journalEntryId()).isEqualTo("entry-usd"); // same-currency wins
        assertThat(m.crossCurrency()).isFalse();
        // The KRW internal is NOT consumed by the USD external → it stays unmatched.
        assertThat(result.discrepancyCount()).isEqualTo(1);
        ReconciliationDiscrepancy d = result.discrepancies().get(0);
        assertThat(d.type()).isEqualTo(DiscrepancyType.UNMATCHED_INTERNAL);
        assertThat(d.journalEntryId()).isEqualTo("entry-krw");
    }

    @Test
    @DisplayName("(mutual exclusion) a KRW external still uses the FIN-BE-021 forward pass only — a "
            + "base-matching foreign internal cross-matches, the reverse pass is never entered")
    void krwExternalStillUsesForwardCrossCurrencyPass() {
        // KRW external 130000; one foreign (USD) internal carrying base 130000. This is the
        // FIN-BE-021 path (KRW-external → foreign-internal), byte-unchanged — NOT the reverse.
        ExternalStatementLine e1 = ext("KRWEXT", 130_000, EntryDirection.DEBIT);
        List<InternalLine> internals = List.of(
                internalUsd("entry-fx", 10_000, EntryDirection.DEBIT, 130_000L));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1), internals, EXACT, AT);

        assertThat(e1.isMatched()).isTrue();
        assertThat(result.matchedCount()).isEqualTo(1);
        assertThat(result.discrepancyCount()).isZero();
        ReconciliationMatch m = result.matches().get(0);
        assertThat(m.journalEntryId()).isEqualTo("entry-fx");
        assertThat(m.crossCurrency()).isTrue();
        // The match carries the external KRW money (FIN-BE-021), not the internal USD amount.
        assertThat(m.money()).isEqualTo(krw(130_000));
    }

    @Test
    @DisplayName("reverse cross determinism — first not-consumed KRW internal by input order")
    void reverseCrossCurrencyDeterministicFirstNotConsumed() {
        // Two foreign externals each carrying base 130000; two KRW internals each 130000.
        ExternalStatementLine e1 = extUsd("FXEXT-1", 10_000, EntryDirection.DEBIT, 130_000L);
        ExternalStatementLine e2 = extUsd("FXEXT-2", 11_000, EntryDirection.DEBIT, 130_000L);
        List<InternalLine> internals = List.of(
                internal("entry-krw-a", 130_000, EntryDirection.DEBIT),
                internal("entry-krw-b", 130_000, EntryDirection.DEBIT));

        ReconciliationResult result = ReconciliationMatcher.match(
                TENANT, STATEMENT, CODE, List.of(e1, e2), internals, EXACT, AT);

        assertThat(result.matchedCount()).isEqualTo(2);
        assertThat(result.discrepancyCount()).isZero();
        // e1 consumes the first KRW internal, e2 the next (deterministic, input order).
        assertThat(result.matches().get(0).journalEntryId()).isEqualTo("entry-krw-a");
        assertThat(result.matches().get(1).journalEntryId()).isEqualTo("entry-krw-b");
        assertThat(result.matches()).allMatch(ReconciliationMatch::crossCurrency);
    }
}
