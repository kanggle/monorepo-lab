package com.example.finance.ledger.application;

import com.example.finance.ledger.domain.error.LedgerErrors.IdempotencyKeyRequiredException;
import com.example.finance.ledger.domain.error.LedgerErrors.JournalEntryNotFoundException;
import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.repository.JournalRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit test for {@link LedgerWriteSupport} — the shared idempotency/replay/audit-reason
 * scaffold extracted from the three operator-facing journal write use cases
 * (TASK-FIN-BE-037, behaviour-preserving refactor). Proves the exact contract the three
 * use cases relied on before the extraction: key validation messages + the 50-char limit,
 * the replay lookup's composed not-found message, the memo→reference→fallback precedence,
 * and a non-blank distinct entry id.
 */
class LedgerWriteSupportTest {

    private static final String TENANT = "finance";

    @Nested
    @DisplayName("validateIdempotencyKey")
    class ValidateIdempotencyKey {

        @Test
        @DisplayName("a present, in-limit key passes")
        void inLimitKeyPasses() {
            assertThatCode(() -> LedgerWriteSupport.validateIdempotencyKey("k-123"))
                    .doesNotThrowAnyException();
            assertThatCode(() -> LedgerWriteSupport.validateIdempotencyKey(
                    "x".repeat(LedgerWriteSupport.MAX_IDEMPOTENCY_KEY_LENGTH)))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("null / blank → IdempotencyKeyRequiredException(required)")
        void nullOrBlankRejected() {
            assertThatThrownBy(() -> LedgerWriteSupport.validateIdempotencyKey(null))
                    .isInstanceOf(IdempotencyKeyRequiredException.class)
                    .hasMessage("Idempotency-Key header is required");
            assertThatThrownBy(() -> LedgerWriteSupport.validateIdempotencyKey("   "))
                    .isInstanceOf(IdempotencyKeyRequiredException.class)
                    .hasMessage("Idempotency-Key header is required");
        }

        @Test
        @DisplayName("over the 50-char limit → IdempotencyKeyRequiredException(at most 50)")
        void overLimitRejected() {
            String tooLong = "x".repeat(LedgerWriteSupport.MAX_IDEMPOTENCY_KEY_LENGTH + 1);
            assertThatThrownBy(() -> LedgerWriteSupport.validateIdempotencyKey(tooLong))
                    .isInstanceOf(IdempotencyKeyRequiredException.class)
                    .hasMessage("Idempotency-Key must be at most 50 characters");
        }

        @Test
        @DisplayName("the limit is 50 (the longest \"<prefix>:\"+key fits the 64-char column)")
        void limitIsFifty() {
            assertThat(LedgerWriteSupport.MAX_IDEMPOTENCY_KEY_LENGTH).isEqualTo(50);
        }
    }

    @Nested
    @DisplayName("requireReplayEntry")
    class RequireReplayEntry {

        @Test
        @DisplayName("found → returns the original entry")
        void foundReturnsEntry() {
            JournalRepository repo = mock(JournalRepository.class);
            JournalEntry entry = mock(JournalEntry.class);
            when(repo.findBySourceEventId("settle:k", TENANT)).thenReturn(Optional.of(entry));

            JournalEntry result = LedgerWriteSupport.requireReplayEntry(repo, "settle:k", TENANT, "settlement");

            assertThat(result).isSameAs(entry);
        }

        @Test
        @DisplayName("not found → JournalEntryNotFoundException with the label-composed message")
        void notFoundThrowsWithLabel() {
            JournalRepository repo = mock(JournalRepository.class);
            when(repo.findBySourceEventId("manual:k", TENANT)).thenReturn(Optional.empty());

            assertThatThrownBy(() ->
                    LedgerWriteSupport.requireReplayEntry(repo, "manual:k", TENANT, "manual"))
                    .isInstanceOf(JournalEntryNotFoundException.class)
                    .hasMessage("manual entry for idempotency key not found (replay): manual:k");
        }
    }

    @Nested
    @DisplayName("auditReason")
    class AuditReason {

        @Test
        @DisplayName("a non-blank memo wins")
        void memoWins() {
            assertThat(LedgerWriteSupport.auditReason("memo", "ref", "fallback")).isEqualTo("memo");
        }

        @Test
        @DisplayName("blank/null memo falls through to a non-blank reference")
        void referenceWhenMemoBlank() {
            assertThat(LedgerWriteSupport.auditReason(null, "ref", "fallback")).isEqualTo("ref");
            assertThat(LedgerWriteSupport.auditReason("  ", "ref", "fallback")).isEqualTo("ref");
        }

        @Test
        @DisplayName("blank/null memo and reference fall through to the fallback default")
        void fallbackWhenBothBlank() {
            assertThat(LedgerWriteSupport.auditReason(null, null, "fallback")).isEqualTo("fallback");
            assertThat(LedgerWriteSupport.auditReason("", "  ", "fallback")).isEqualTo("fallback");
        }
    }

    @Test
    @DisplayName("newEntryId → non-blank and distinct across calls")
    void newEntryIdNonBlankDistinct() {
        String a = LedgerWriteSupport.newEntryId();
        String b = LedgerWriteSupport.newEntryId();
        assertThat(a).isNotBlank();
        assertThat(b).isNotBlank();
        assertThat(a).isNotEqualTo(b);
    }
}
