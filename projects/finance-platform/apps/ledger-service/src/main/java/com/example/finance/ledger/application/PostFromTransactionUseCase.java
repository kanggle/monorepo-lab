package com.example.finance.ledger.application;

import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.ProcessedEventStore;
import com.example.finance.ledger.domain.error.LedgerErrors.JournalEntryNotFoundException;
import com.example.finance.ledger.domain.journal.CompletedTransaction;
import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.PostingPolicy;
import com.example.finance.ledger.domain.journal.SourceRef;
import com.example.finance.ledger.domain.journal.repository.JournalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Maps an account-service transaction event to a balanced journal entry and
 * posts it idempotently (architecture.md § Posting Policy / § Idempotency). The
 * single {@code @Transactional} boundary that wraps the dedupe check + insert,
 * the entry + lines, and the audit row (F1 — at-most-once posting per signed
 * source event id).
 *
 * <ul>
 *   <li><b>completed.v1</b> → {@link PostingPolicy#toEntry}; HOLD/RELEASE post no
 *       entry (no-op ACK, documented).</li>
 *   <li><b>reversed.v1</b> → look up the original entry by source transaction id,
 *       build a swapped REVERSAL entry referencing it (F3); a missing original is
 *       a real anomaly → {@link JournalEntryNotFoundException} (→ DLT).</li>
 * </ul>
 *
 * A re-delivered event ({@code eventId} already in {@code processed_events}) is a
 * no-op.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostFromTransactionUseCase {

    private final PostJournalEntryUseCase postJournalEntry;
    private final JournalRepository journalRepository;
    private final ProcessedEventStore processedEvents;
    private final ClockPort clock;

    /**
     * @return the posted entry, or {@link Optional#empty()} for a no-op
     *         (duplicate event, or HOLD/RELEASE — both are valid ACK-only outcomes)
     */
    @Transactional
    public Optional<JournalEntry> post(PostFromTransactionCommand cmd) {
        if (processedEvents.isProcessed(cmd.eventId())) {
            log.debug("Duplicate transaction event skipped: eventId={} topic={}",
                    cmd.eventId(), cmd.topic());
            return Optional.empty();
        }
        Instant now = clock.now();
        CompletedTransaction txn = cmd.transaction();
        SourceRef source = SourceRef.ofTransaction(txn.transactionId(), cmd.eventId());

        Optional<JournalEntry> posted;
        if (cmd.reversal()) {
            posted = Optional.of(postReversal(cmd, source, now));
        } else {
            posted = postForward(cmd, source, now);
        }

        // The dedupe row is recorded in this SAME transaction whether or not an
        // entry was posted — a HOLD/RELEASE event is "processed" (no-op) so a
        // re-delivery is not reconsidered.
        processedEvents.markProcessed(cmd.eventId(), txn.tenantId(), cmd.topic(),
                txn.transactionId(), now);
        return posted;
    }

    private Optional<JournalEntry> postForward(PostFromTransactionCommand cmd,
                                               SourceRef source, Instant now) {
        Optional<JournalEntry> entry = PostingPolicy.toEntry(
                UUID.randomUUID().toString(), now, source, cmd.transaction());
        if (entry.isEmpty()) {
            log.debug("No ledger entry for {} transaction {} (held/available is single-entry)",
                    cmd.transaction().type(), cmd.transaction().transactionId());
            return Optional.empty();
        }
        return Optional.of(postJournalEntry.post(entry.get(),
                "auto-journal of transaction " + cmd.transaction().transactionId()));
    }

    private JournalEntry postReversal(PostFromTransactionCommand cmd,
                                      SourceRef source, Instant now) {
        String originalTxnId = cmd.reversalOfTransactionId();
        JournalEntry original = journalRepository
                .findBySourceTransactionId(originalTxnId, cmd.transaction().tenantId())
                .orElseThrow(() -> new JournalEntryNotFoundException(
                        "no original journal entry for reversed transaction " + originalTxnId));
        JournalEntry reversal = JournalEntry.reversalEntry(
                UUID.randomUUID().toString(), now, source, original);
        return postJournalEntry.post(reversal,
                "reversal of transaction " + originalTxnId
                        + " (original entry " + original.entryId() + ")");
    }
}
