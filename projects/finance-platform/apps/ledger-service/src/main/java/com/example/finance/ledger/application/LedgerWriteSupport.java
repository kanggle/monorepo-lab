package com.example.finance.ledger.application;

import com.example.finance.ledger.domain.error.LedgerErrors.IdempotencyKeyRequiredException;
import com.example.finance.ledger.domain.error.LedgerErrors.JournalEntryNotFoundException;
import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.repository.JournalRepository;

import java.util.UUID;

/**
 * Shared scaffolding for the operator-facing journal <b>write</b> use cases —
 * {@link PostManualJournalEntryUseCase} (5th increment), {@link RevalueForeignBalanceUseCase}
 * (9th increment), and {@link SettleForeignPositionUseCase} (10th increment). These three
 * previously duplicated, byte-for-byte, the same client-{@code Idempotency-Key} contract,
 * replay lookup, audit-reason fallback chain, and entry-id minting. Centralising them here
 * keeps that contract single-sourced without altering behaviour: same exception types, same
 * messages, same {@link #MAX_IDEMPOTENCY_KEY_LENGTH} limit, same audit-reason precedence.
 *
 * <p>Pure and stateless — a {@code final} utility with a private constructor, deliberately
 * <b>not</b> a Spring bean (no injection point, no lifecycle). Each use case still owns its own
 * {@code DEDUPE_PREFIX} / {@code DEDUPE_TOPIC} (genuinely per-use-case) and all FX/journal math.
 */
final class LedgerWriteSupport {

    /**
     * Max client {@code Idempotency-Key} length. The namespaced dedupe key is
     * {@code "<prefix>:" + key}; the longest prefix ({@code "settle:"} / {@code "manual:"} = 7)
     * plus 50 = 57, which fits the 64-char {@code processed_events} key column.
     */
    static final int MAX_IDEMPOTENCY_KEY_LENGTH = 50;

    private LedgerWriteSupport() {
    }

    /**
     * Validate a client idempotency key: it must be present (non-blank) and at most
     * {@link #MAX_IDEMPOTENCY_KEY_LENGTH} characters. Throws
     * {@link IdempotencyKeyRequiredException} otherwise (→ the controller maps it to 400/422).
     */
    static void validateIdempotencyKey(String key) {
        if (key == null || key.isBlank()) {
            throw new IdempotencyKeyRequiredException("Idempotency-Key header is required");
        }
        if (key.length() > MAX_IDEMPOTENCY_KEY_LENGTH) {
            throw new IdempotencyKeyRequiredException(
                    "Idempotency-Key must be at most " + MAX_IDEMPOTENCY_KEY_LENGTH + " characters");
        }
    }

    /**
     * Look up the original entry for an already-processed dedupe key (an idempotent replay).
     * The unique constraint on {@code processed_events} makes a concurrent double-submit
     * race-safe (the loser lands here). Fails with {@link JournalEntryNotFoundException} when
     * the dedupe row exists but its entry cannot be found.
     *
     * @param entryLabel the per-use-case noun (e.g. {@code "manual"}, {@code "revaluation"},
     *                   {@code "settlement"}) composed into the not-found message
     */
    static JournalEntry requireReplayEntry(JournalRepository journalRepository, String dedupeKey,
                                           String tenantId, String entryLabel) {
        return journalRepository.findBySourceEventId(dedupeKey, tenantId)
                .orElseThrow(() -> new JournalEntryNotFoundException(
                        entryLabel + " entry for idempotency key not found (replay): " + dedupeKey));
    }

    /**
     * Resolve the audit reason via the fallback chain: a non-blank {@code memo} wins, else a
     * non-blank {@code reference}, else the use-case {@code fallback} default.
     */
    static String auditReason(String memo, String reference, String fallback) {
        if (memo != null && !memo.isBlank()) {
            return memo;
        }
        if (reference != null && !reference.isBlank()) {
            return reference;
        }
        return fallback;
    }

    /** A fresh random journal-entry id. */
    static String newEntryId() {
        return UUID.randomUUID().toString();
    }
}
