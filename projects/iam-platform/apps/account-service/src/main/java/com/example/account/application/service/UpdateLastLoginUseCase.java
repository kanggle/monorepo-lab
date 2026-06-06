package com.example.account.application.service;

import com.example.account.domain.account.Account;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.tenant.TenantId;
import com.example.messaging.outbox.ProcessedEventJpaEntity;
import com.example.messaging.outbox.ProcessedEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

/**
 * Updates {@code accounts.last_login_succeeded_at} in response to an
 * {@code auth.login.succeeded} Kafka event consumed by
 * {@link com.example.account.infrastructure.kafka.LoginSucceededConsumer}.
 *
 * <p>Idempotency is enforced at the DB level via {@link ProcessedEventJpaRepository}
 * keyed on {@code eventId}. The dedup row insert and the account update happen
 * in the same {@link Transactional} so that a redelivery either sees the dedup
 * row (skip) or replays the full update from a clean slate (rollback).
 *
 * <p>Account-not-found is treated as a soft failure (WARN log + return) to
 * avoid poisoning the consumer when an event for a deleted/never-existed
 * account arrives — the alternative (RuntimeException) would block the
 * partition forever.
 *
 * <p>See TASK-BE-103, specs/contracts/events/auth-events.md.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UpdateLastLoginUseCase {

    private static final String EVENT_TYPE = "auth.login.succeeded";

    private final AccountRepository accountRepository;
    private final ProcessedEventJpaRepository processedEventRepository;

    @Transactional(noRollbackFor = DataIntegrityViolationException.class)
    public void execute(String eventId, String accountId, Instant occurredAt) {
        if (processedEventRepository.existsByEventId(eventId)) {
            log.info("Duplicate auth.login.succeeded event skipped: eventId={}", eventId);
            return;
        }

        try {
            processedEventRepository.saveAndFlush(
                    ProcessedEventJpaEntity.create(eventId, EVENT_TYPE));
        } catch (DataIntegrityViolationException e) {
            // Concurrent redelivery: another consumer thread won the dedup-row
            // insert race. saveAndFlush() forces the INSERT SQL to execute
            // immediately so the constraint violation is raised inside this
            // try/catch (instead of at commit-time deferred flush). The
            // noRollbackFor on @Transactional keeps the transaction commit-able
            // after this catch — we exit cleanly without touching the account
            // because the concurrent winner already performed the update.
            log.info("ProcessedEvent insert lost a redelivery race: eventId={}", eventId);
            return;
        }

        // TASK-BE-228: tenant context is fixed to FAN_PLATFORM until TASK-BE-229.
        // The auth.login.succeeded event does not yet carry tenant_id (TASK-BE-229).
        Optional<Account> maybeAccount = accountRepository.findById(TenantId.FAN_PLATFORM, accountId);
        if (maybeAccount.isEmpty()) {
            // Poison-pill guard — log and return without throwing so that the
            // consumer commits the offset and moves on. Missing accounts can
            // legitimately occur for hard-deleted (post-anonymization) ids.
            log.warn("auth.login.succeeded for unknown accountId={}, eventId={} — skipping",
                    accountId, eventId);
            return;
        }

        Account account = maybeAccount.get();
        account.recordLoginSuccess(occurredAt);
        accountRepository.save(account);
    }
}
