package com.example.finance.ledger.infrastructure.fxrate;

import com.example.finance.ledger.application.RefreshFxRateQuotesUseCase;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled poller that refreshes the FX rate quote cache (23rd increment — TASK-FIN-BE-031,
 * ADR-002 D4). Mirrors the {@code LedgerOutboxPublisher} {@code @Scheduled}+gate idiom, BUT the
 * gate {@code financeplatform.ledger.fxrate.enabled} carries <b>NO</b> {@code matchIfMissing} — so
 * the bean is created ONLY when explicitly enabled. Default OFF = net-zero (no poller, no external
 * call, empty cache).
 *
 * <p><b>Best-effort, never-throw</b> (AC-6): the tick wraps {@link RefreshFxRateQuotesUseCase} in a
 * catch-all, so a transient failure logs and the scheduler survives to the next tick. (No ShedLock
 * single-leader guard in this service — ADR-002 D4's "ShedLock" is a sketch; multi-instance
 * single-leader is deferred. The current {@code @Scheduled}+gate pattern is retained.)
 */
@Component
@ConditionalOnProperty(name = "financeplatform.ledger.fxrate.enabled", havingValue = "true")
@RequiredArgsConstructor
public class FxRateFeedPoller {

    private static final Logger log = LoggerFactory.getLogger(FxRateFeedPoller.class);

    private final RefreshFxRateQuotesUseCase refreshFxRateQuotesUseCase;

    @Scheduled(
            fixedDelayString = "${financeplatform.ledger.fxrate.poll-interval-ms:60000}",
            initialDelayString = "${financeplatform.ledger.fxrate.initial-delay-ms:5000}")
    public void poll() {
        try {
            int upserted = refreshFxRateQuotesUseCase.refresh();
            if (log.isDebugEnabled()) {
                log.debug("FX_RATE_POLL_OK upserted={}", upserted);
            }
        } catch (Exception e) {
            // never throw — keep the scheduler alive across provider/DB hiccups.
            log.warn("FX_RATE_POLL_FAILED", e);
        }
    }
}
