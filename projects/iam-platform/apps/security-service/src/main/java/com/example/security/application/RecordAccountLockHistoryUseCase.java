package com.example.security.application;

import com.example.security.domain.history.AccountLockHistory;
import com.example.security.domain.repository.AccountLockHistoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * Records an immutable {@code account_lock_history} row from a consumed
 * {@code account.locked} event (TASK-BE-041b).
 *
 * <p>Idempotency: the {@code account_lock_history.event_id} unique constraint naturally
 * deduplicates Kafka at-least-once replays. A {@link DataIntegrityViolationException}
 * from a duplicate {@code event_id} is treated as success (already processed) and must
 * NOT propagate — otherwise the shared {@code DefaultErrorHandler} would route a
 * harmless duplicate to {@code account.locked.dlq}.
 *
 * <p>No outer {@code @Transactional} boundary: this is a single-row append, so the
 * repository save runs in its own transaction and the duplicate-key violation can be
 * caught cleanly here. An outer transaction would be marked rollback-only by the
 * violation and then fail on commit.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordAccountLockHistoryUseCase {

    private final AccountLockHistoryRepository accountLockHistoryRepository;

    /**
     * Appends the account-lock history row.
     *
     * @return {@code true} if recorded, {@code false} if a duplicate was ignored
     */
    public boolean execute(AccountLockHistory entry) {
        try {
            accountLockHistoryRepository.save(entry);
            log.info("account.locked recorded: eventId={}, accountId={}, source={}",
                    entry.getEventId(), entry.getAccountId(), entry.getSource());
            return true;
        } catch (DataIntegrityViolationException dup) {
            log.info("account.locked duplicate ignored (event_id unique): eventId={}", entry.getEventId());
            return false;
        }
    }
}
