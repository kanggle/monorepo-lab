package com.example.account.infrastructure.scheduler;

import com.example.account.application.command.ChangeStatusCommand;
import com.example.account.application.service.AccountStatusUseCase;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.status.StatusChangeReason;
import com.example.account.infrastructure.persistence.AccountJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Daily dormant transition batch (retention.md §1).
 *
 * <p>Selects ACTIVE accounts whose {@code last_login_succeeded_at} (falling back
 * to {@code created_at}) is older than 365 days, then transitions each one to
 * DORMANT via {@link AccountStatusUseCase}. Per-account failure isolation:
 * a single account's failure must not block the rest of the batch
 * (retention.md §1.7).
 *
 * <p>Trigger: {@code @Scheduled(cron = "0 0 2 * * *", zone = "UTC")} — daily at 02:00 UTC.
 */
@Slf4j
@Component
@Profile("!standalone")
@ConditionalOnProperty(name = "scheduler.dormant.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
public class AccountDormantScheduler {

    private static final String METRIC_PROCESSED = "scheduler.dormant.processed";
    private static final String METRIC_FAILED = "scheduler.dormant.failed";
    private static final String METRIC_DURATION = "scheduler.dormant.duration_ms";

    private final AccountJpaRepository accountJpaRepository;
    private final AccountStatusUseCase accountStatusUseCase;
    private final MeterRegistry meterRegistry;

    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")
    public void runDormantBatch() {
        Timer.Sample sample = Timer.start(meterRegistry);
        try {
            Instant threshold = Instant.now().minus(365, ChronoUnit.DAYS);
            var candidates = accountJpaRepository.findActiveDormantCandidates(threshold);

            if (candidates.isEmpty()) {
                log.info("[DormantScheduler] no candidates found (threshold={})", threshold);
                return;
            }

            AtomicInteger processed = new AtomicInteger();
            AtomicInteger failed = new AtomicInteger();

            for (var entity : candidates) {
                try {
                    accountStatusUseCase.changeStatus(new ChangeStatusCommand(
                            entity.getId(),
                            AccountStatus.DORMANT,
                            StatusChangeReason.DORMANT_365D,
                            "system",
                            null,
                            null
                    ));
                    meterRegistry.counter(METRIC_PROCESSED).increment();
                    processed.incrementAndGet();
                } catch (Exception e) {
                    meterRegistry.counter(METRIC_FAILED).increment();
                    failed.incrementAndGet();
                    log.warn("[DormantScheduler] failed to transition account {} to DORMANT: {}",
                            entity.getId(), e.getMessage());
                }
            }

            log.info("[DormantScheduler] done — processed={}, failed={}", processed.get(), failed.get());
        } finally {
            sample.stop(meterRegistry.timer(METRIC_DURATION));
        }
    }
}
