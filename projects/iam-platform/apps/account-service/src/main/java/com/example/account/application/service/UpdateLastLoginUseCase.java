package com.example.account.application.service;

import com.example.account.domain.account.Account;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.tenant.TenantId;
import com.example.account.infrastructure.persistence.ProcessedEventJpaEntity;
import com.example.account.infrastructure.persistence.ProcessedEventJpaRepository;
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

    /**
     * NET-ZERO overload — a legacy event with no {@code tenantId} in its payload stays
     * pinned to {@link TenantId#FAN_PLATFORM}, byte-identical to the pre-BE-507 behaviour.
     */
    @Transactional(noRollbackFor = DataIntegrityViolationException.class)
    public void execute(String eventId, String accountId, Instant occurredAt) {
        execute(eventId, accountId, TenantId.FAN_PLATFORM, occurredAt);
    }

    /**
     * TASK-BE-507 — tenant-aware last-login update. The {@code auth.login.succeeded}
     * payload has carried a required {@code tenantId} since TASK-BE-248 (auth-events.md
     * schema v2, fail-closed at the emitter); this consumer simply never read it, so a
     * login by any non-fan account silently no-opped through the poison-pill guard below.
     */
    @Transactional(noRollbackFor = DataIntegrityViolationException.class)
    public void execute(String eventId, String accountId, TenantId tenantId, Instant occurredAt) {
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

        Optional<Account> maybeAccount = accountRepository.findById(tenantId, accountId);
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
