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
 */
class ReconciliationMatcherTest {

    private static final String TENANT = "finance";
    private static final String STATEMENT = "stmt-1";
    private static final String CODE = LedgerAccountCodes.CASH_CLEARING;
    private static final Instant AT = Instant.parse("2026-01-31T00:00:00Z");
    private static final LocalDate VALUE_DATE = LocalDate.parse("2026-01-15");

    private static Money krw(long m) {
        return Money.of(m, Currency.KRW);
    }

    private static ExternalStatementLine ext(String ref, long amount, EntryDirection dir) {
        return ExternalStatementLine.of(null, STATEMENT, TENANT, ref, krw(amount), dir,
                VALUE_DATE, null);
    }

    private static InternalLine internal(String entryId, long amount, EntryDirection dir) {
        return new InternalLine(entryId, CODE, dir, krw(amount));
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
                TENANT, STATEMENT, CODE, List.of(e1, e2), internals, AT);

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
                TENANT, STATEMENT, CODE, List.of(e1, e2), internals, AT);

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
                TENANT, STATEMENT, CODE, List.of(e1), internals, AT);

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
                TENANT, STATEMENT, CODE, List.of(e1, e2), internals, AT);

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
                TENANT, STATEMENT, CODE, List.of(e1), internals, AT);

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
                TENANT, STATEMENT, CODE, List.of(e1, e2), internals, AT);

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
                TENANT, STATEMENT, CODE, List.of(), List.of(), AT);

        assertThat(result.matchedCount()).isZero();
        assertThat(result.discrepancyCount()).isZero();
    }
}
