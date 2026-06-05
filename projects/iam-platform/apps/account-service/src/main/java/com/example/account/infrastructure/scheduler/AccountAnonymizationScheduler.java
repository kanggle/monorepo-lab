package com.example.account.infrastructure.scheduler;

import com.example.account.application.event.AccountEventPublisher;
import com.example.account.domain.account.Account;
import com.example.account.domain.history.AccountStatusHistoryEntry;
import com.example.account.domain.repository.AccountStatusHistoryRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.infrastructure.anonymizer.PiiAnonymizer;
import com.example.account.infrastructure.persistence.AccountJpaEntity;
import com.example.account.infrastructure.persistence.AccountJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Daily PII anonymization batch (retention.md §2).
 *
 * <p>Selects DELETED accounts whose grace period has expired (now - 30d) and whose
 * profile is not yet masked, then runs {@link PiiAnonymizer#anonymize(Account)}
 * for each, stamps {@code masked_at} on the profile (handled by the anonymizer),
 * and re-publishes {@code account.deleted} with {@code anonymized=true}
 * (account-events.md §account.deleted).
 *
 * <p>Trigger: {@code @Scheduled(cron = "0 0 3 * * *", zone = "UTC")} — daily at 03:00 UTC.
 *
 * <p>Failure handling (retention.md §2.9): per-account try/catch with WARN log;
 * one account's failure must not block the rest of the batch. Each successful
 * account is committed in its own transaction (per-account boundary,
 * {@link Propagation#REQUIRES_NEW}).
 */
@Slf4j
@Component
@Profile("!standalone")
@ConditionalOnProperty(name = "scheduler.anonymize.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AccountAnonymizationScheduler {

    static final Duration GRACE_PERIOD = Duration.ofDays(30);

    private static final String METRIC_PROCESSED = "scheduler.anonymize.processed";
    private static final String METRIC_FAILED = "scheduler.anonymize.failed";
    private static final String METRIC_DURATION = "scheduler.anonymize.duration_ms";

    private final AccountJpaRepository accountJpaRepository;
    private final AnonymizationTransaction anonymizationTransaction;
    private final MeterRegistry meterRegistry;

    /**
     * Run the daily anonymization batch. Visible for tests.
     */
    @Scheduled(cron = "0 0 3 * * *", zone = "UTC")
    public void runAnonymizationBatch() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Instant threshold = Instant.now().minus(GRACE_PERIOD);
            List<AccountJpaEntity> candidates = accountJpaRepository.findAnonymizationCandidates(threshold);

            if (candidates.isEmpty()) {
                log.debug("Anonymization batch: no candidates older than {}", threshold);
                return;
            }

            log.info("Anonymization batch starting: candidates={}, threshold={}",
                    candidates.size(), threshold);

            int processed = 0;
            int failed = 0;
            for (AccountJpaEntity entity : candidates) {
                String accountId = entity.getId();
                try {
                    anonymizationTransaction.anonymizeOne(accountId);
                    meterRegistry.counter(METRIC_PROCESSED).increment();
                    processed++;
                } catch (Exception e) {
                    meterRegistry.counter(METRIC_FAILED).increment();
                    failed++;
                    log.warn("Anonymization failed for accountId={}; skipping. cause={}",
                            accountId, e.toString());
                }
            }

            log.info("Anonymization batch complete: processed={}, failed={}", processed, failed);
        } finally {
            sample.stop(meterRegistry.timer(METRIC_DURATION));
        }
    }

    /**
     * Per-account transactional unit. Lives in its own bean so that
     * {@link Transactional} works via Spring AOP (self-invocation would bypass it).
     */
    @Component
    @RequiredArgsConstructor
    static class AnonymizationTransaction {

        private final AccountJpaRepository accountJpaRepository;
        private final PiiAnonymizer piiAnonymizer;
        private final AccountEventPublisher eventPublisher;
        private final AccountStatusHistoryRepository statusHistoryRepository;

        /**
         * Re-load the account inside this transaction (so optimistic locking + status
         * re-check can catch concurrent grace-period recovery) and run anonymization.
         *
         * <p>Resolves the original deletion {@code reasonCode} by querying
         * {@code account_status_history} for the most recent transition row that ended in
         * {@code DELETED} (n+1 query; batch size is bounded by retention.md §2 candidate
         * count which is operationally small). Falls back to {@code USER_REQUEST} with a
         * WARN log when no DELETED transition row is found (data inconsistency safeguard).
         *
         * <p>Per retention.md §2.7 (revised), the re-published
         * {@code account.deleted(anonymized=true)} payload carries the original
         * {@code reasonCode} (audit fidelity), but always uses {@code actorType="system"}
         * and {@code actorId="anonymization-batch"} because the anonymization is performed
         * by the scheduled batch process, not by the original human/system actor that
         * initiated the deletion.
         */
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public void anonymizeOne(String accountId) {
            AccountJpaEntity managed = accountJpaRepository.findById(accountId)
                    .orElseThrow(() -> new IllegalStateException(
                            "Account disappeared mid-batch: " + accountId));

            Account account = managed.toDomain();
            if (account.getStatus() != AccountStatus.DELETED) {
                throw new IllegalStateException(
                        "Account no longer DELETED (concurrent recovery?): " + accountId);
            }

            piiAnonymizer.anonymize(account);

            // Resolve only the reasonCode from account_status_history for audit fidelity.
            // actorType and actorId are always "system" / "anonymization-batch" because
            // this re-publication is performed by the batch scheduler, not the original
            // deletion actor (retention.md §2.7 revised).
            String reasonCode = resolveOriginalReasonCode(accountId);

            // gracePeriodEndsAt = original deletedAt + 30d (retention.md §2.7,
            // "원래 유예 종료 시각"). This is the contract-defined semantic for the
            // account-events.md account.deleted payload, NOT the anonymization timestamp.
            Instant deletedAt = account.getDeletedAt();
            Instant gracePeriodEndsAt = deletedAt.plus(GRACE_PERIOD);

            // Re-publish account.deleted with anonymized=true.
            // actorType=system, actorId=anonymization-batch: the scheduler is always the
            // acting system for this re-publication (retention.md §2.7).
            eventPublisher.publishAccountDeletedAnonymized(
                    account,
                    account.getTenantId().value(),
                    reasonCode,
                    "system",
                    "anonymization-batch",
                    deletedAt,
                    gracePeriodEndsAt);
        }

        private String resolveOriginalReasonCode(String accountId) {
            Optional<AccountStatusHistoryEntry> lastDeleted = statusHistoryRepository
                    .findByAccountIdOrderByOccurredAtDesc(accountId)
                    .stream()
                    .filter(entry -> entry.getToStatus() == AccountStatus.DELETED)
                    .findFirst();

            if (lastDeleted.isPresent()) {
                return lastDeleted.get().getReasonCode().name();
            }

            log.warn(
                    "No DELETED transition history found for accountId={}; "
                            + "falling back to reasonCode={} for anonymized event re-publish.",
                    accountId, StatusChangeReason.USER_REQUEST.name());
            return StatusChangeReason.USER_REQUEST.name();
        }
    }
}
