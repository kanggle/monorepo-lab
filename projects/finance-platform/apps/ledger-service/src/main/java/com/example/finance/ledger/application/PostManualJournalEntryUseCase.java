package com.example.finance.ledger.application;

import com.example.finance.ledger.application.PostManualJournalEntryCommand.ManualLine;
import com.example.finance.ledger.application.port.outbound.ClockPort;
import com.example.finance.ledger.application.port.outbound.ProcessedEventStore;
import com.example.finance.ledger.domain.account.repository.LedgerAccountRepository;
import com.example.finance.ledger.domain.error.LedgerErrors.IdempotencyKeyRequiredException;
import com.example.finance.ledger.domain.error.LedgerErrors.JournalEntryNotFoundException;
import com.example.finance.ledger.domain.error.LedgerErrors.LedgerAccountNotFoundException;
import com.example.finance.ledger.domain.journal.JournalEntry;
import com.example.finance.ledger.domain.journal.JournalLine;
import com.example.finance.ledger.domain.journal.SourceRef;
import com.example.finance.ledger.domain.journal.repository.JournalRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Manual journal posting (5th increment, TASK-FIN-BE-011 — architecture.md
 * § Manual Journal Posting). An operator posts an adjusting entry directly. This
 * is the first journal <b>mutation</b> use case; it adds <b>no new write
 * boundary</b> — it builds a balanced {@link JournalEntry} and funnels it through
 * the existing {@link PostJournalEntryUseCase#post(JournalEntry, String, String)}
 * (the single guarded write path), inheriting the balance identity, the
 * closed-period guard, the audit row (actor = the operator subject), and the
 * {@code entry.posted} outbox append unchanged.
 *
 * <p>Idempotent (F1) on a client {@code Idempotency-Key}: the key namespaces into
 * the existing {@code processed_events} dedupe as {@code manual:{key}}. A replay
 * returns the original entry (no re-post); a first request marks the key processed
 * in the SAME Tx (the unique constraint makes a concurrent double-submit
 * race-safe). Referenced accounts must already exist — the manual path does
 * <b>not</b> lazily mint a chart node (unlike the auto-journal consumer).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostManualJournalEntryUseCase {

    static final String DEDUPE_PREFIX = "manual:";
    static final String DEDUPE_TOPIC = "manual-posting";
    /** Max client key length — {@code "manual:" + key} (57) must fit the 64-char column. */
    static final int MAX_KEY_LENGTH = 50;

    private final JournalRepository journalRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final ProcessedEventStore processedEventStore;
    private final PostJournalEntryUseCase postJournalEntryUseCase;
    private final ClockPort clock;

    /**
     * The post outcome — the entry plus whether it was an idempotent replay (the
     * controller maps {@code replayed=false → 201}, {@code replayed=true → 200}).
     */
    public record Result(JournalEntry entry, boolean replayed) {
    }

    @Transactional
    public Result post(PostManualJournalEntryCommand cmd) {
        String key = cmd.idempotencyKey();
        if (key == null || key.isBlank()) {
            throw new IdempotencyKeyRequiredException("Idempotency-Key header is required");
        }
        if (key.length() > MAX_KEY_LENGTH) {
            throw new IdempotencyKeyRequiredException(
                    "Idempotency-Key must be at most " + MAX_KEY_LENGTH + " characters");
        }
        String dedupeKey = DEDUPE_PREFIX + key;

        // (1) Idempotent replay — the key was already processed; return the original
        //     entry (no re-post). The unique constraint on processed_events makes a
        //     concurrent double-submit race-safe (the loser lands here).
        if (processedEventStore.isProcessed(dedupeKey)) {
            JournalEntry original = journalRepository
                    .findBySourceEventId(dedupeKey, cmd.tenantId())
                    .orElseThrow(() -> new JournalEntryNotFoundException(
                            "manual entry for idempotency key not found (replay): " + dedupeKey));
            return new Result(original, true);
        }

        // (2) Referenced accounts must already exist (no lazy mint — F4).
        for (ManualLine line : cmd.lines()) {
            if (!ledgerAccountRepository.existsByCode(line.ledgerAccountCode(), cmd.tenantId())) {
                throw new LedgerAccountNotFoundException(
                        "ledger account does not exist: " + line.ledgerAccountCode());
            }
        }

        // (3) Build the balanced entry — the factory self-validates the balance
        //     identity (LEDGER_ENTRY_UNBALANCED) and single-currency (CURRENCY_MISMATCH).
        Instant postedAt = cmd.postedAt() != null ? cmd.postedAt() : clock.now();
        List<JournalLine> lines = new ArrayList<>(cmd.lines().size());
        for (ManualLine line : cmd.lines()) {
            lines.add(JournalLine.of(cmd.tenantId(), line.ledgerAccountCode(),
                    line.direction(), line.money()));
        }
        JournalEntry entry = JournalEntry.post(newEntryId(), cmd.tenantId(), postedAt,
                SourceRef.ofManual(cmd.reference(), dedupeKey), lines);

        // (4) Record the dedupe row in the SAME Tx, then funnel through the guarded
        //     write path (closed-period guard → DLT-free 422 here; audit actor =
        //     operator subject; entry.posted outbox append).
        processedEventStore.markProcessed(dedupeKey, cmd.tenantId(), DEDUPE_TOPIC,
                entry.source().getSourceTransactionId(), clock.now());
        JournalEntry posted = postJournalEntryUseCase.post(entry, reason(cmd), cmd.operatorSubject());
        return new Result(posted, false);
    }

    private static String reason(PostManualJournalEntryCommand cmd) {
        if (cmd.memo() != null && !cmd.memo().isBlank()) {
            return cmd.memo();
        }
        if (cmd.reference() != null && !cmd.reference().isBlank()) {
            return cmd.reference();
        }
        return "manual adjusting entry";
    }

    private static String newEntryId() {
        return UUID.randomUUID().toString();
    }
}
