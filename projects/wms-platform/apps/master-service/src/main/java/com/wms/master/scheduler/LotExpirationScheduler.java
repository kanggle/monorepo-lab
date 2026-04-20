package com.wms.master.scheduler;

import com.wms.master.application.port.in.ExpireLotsBatchUseCase;
import com.wms.master.application.port.in.ExpireLotsBatchUseCase.LotExpirationResult;
import java.time.Clock;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Daily scheduled transition ACTIVE → EXPIRED for Lots whose
 * {@code expiry_date < today}.
 *
 * <p>Cron {@code 0 5 0 * * *} = 00:05 server-local (avoid midnight thundering
 * herd and any DST-flip edge case that {@link LocalDate#now(Clock)} handles
 * idempotently anyway). Per the task spec §Edge Cases:
 * <ul>
 *   <li>A lot with {@code expiryDate == today} does NOT expire until
 *       tomorrow's run (strict {@code <}).
 *   <li>A lot without {@code expiryDate} never expires via the scheduler.
 *   <li>Running twice is idempotent: already-EXPIRED rows no longer match
 *       the query.
 * </ul>
 *
 * <p>The {@code wms.scheduler.lot-expiration.enabled} property gates the
 * bean for test harnesses: {@code application-integration.yml} sets it to
 * {@code false} so integration tests invoke {@link #runNow()} / the use
 * case directly instead of waiting for the cron fire.
 *
 * <p>Event publication flows through the outbox (same tx as the row
 * update) — the scheduler never talks to Kafka directly.
 */
@Component
@ConditionalOnProperty(
        name = "wms.scheduler.lot-expiration.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class LotExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(LotExpirationScheduler.class);

    private final ExpireLotsBatchUseCase useCase;
    private final Clock clock;

    @Autowired
    public LotExpirationScheduler(ExpireLotsBatchUseCase useCase) {
        this(useCase, Clock.systemDefaultZone());
    }

    /**
     * Clock-injecting constructor for tests that need to pin {@code today} to
     * a specific value. The default-zone clock in production matches the
     * server's operational timezone — timezone concerns deferred to v2 per
     * task §Out of Scope.
     */
    LotExpirationScheduler(ExpireLotsBatchUseCase useCase, Clock clock) {
        this.useCase = useCase;
        this.clock = clock;
    }

    /**
     * Cron fire (00:05 daily). Delegates to {@link #runNow()} so tests can
     * exercise the full path without time travel.
     */
    @Scheduled(cron = "0 5 0 * * *")
    public void runScheduled() {
        runNow();
    }

    /**
     * Executes the expiration batch against the clock's current date. Public
     * so that integration / e2e harnesses can trigger it directly after
     * disabling the cron.
     */
    public LotExpirationResult runNow() {
        LocalDate today = LocalDate.now(clock);
        try {
            LotExpirationResult result = useCase.execute(today);
            log.info("LotExpirationScheduler: considered={} expired={} failed={}",
                    result.considered(), result.expired(), result.failed());
            return result;
        } catch (RuntimeException ex) {
            // Top-level safety net: the scheduled thread must not die.
            log.error("LotExpirationScheduler: batch failed", ex);
            return new LotExpirationResult(0, 0, 0);
        }
    }
}
