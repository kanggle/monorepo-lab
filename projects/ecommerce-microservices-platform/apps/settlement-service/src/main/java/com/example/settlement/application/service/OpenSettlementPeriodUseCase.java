package com.example.settlement.application.service;

import com.example.settlement.application.view.PeriodView;
import com.example.settlement.domain.period.PeriodAlreadyOpenException;
import com.example.settlement.domain.period.SettlementPeriod;
import com.example.settlement.domain.repository.SettlementPeriodRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Opens a settlement period (architecture.md § Period close). One
 * {@code @Transactional} boundary: build an OPEN period (the factory validates
 * {@code from < to} → {@link com.example.settlement.domain.period.PeriodWindowInvalidException}
 * → 422 {@code PERIOD_WINDOW_INVALID}) and persist it. The window is operator-supplied
 * and grain-agnostic; no overlap check (a tenant may run overlapping windows — the
 * close folds whichever accruals fall in {@code [from, to)}). Since TASK-BE-535 an
 * <b>exact</b> duplicate is nonetheless refused — see below.
 *
 * <h2>Duplicate-open guard (TASK-BE-535)</h2>
 * A replayed open used to mint another {@code UUID.randomUUID()} period over the same
 * window. That is a double-payout vector: {@code close} folds the in-window accruals
 * into {@code seller_payout} rows, so two OPEN periods over one accrual window, each
 * closed, pay each seller twice.
 *
 * <p>The guard is the Flyway V6 <b>partial unique index</b>
 * {@code (tenant_id, period_from, period_to) WHERE status = 'OPEN'}, not a
 * read-then-write check and not a client {@code Idempotency-Key}:
 * <ul>
 *   <li><b>Concurrency (AC-5)</b> — the constraint is the arbiter, so two simultaneous
 *       in-flight duplicates cannot both commit. A read-then-write check would let both
 *       pass before either committed.</li>
 *   <li><b>No caller change</b> — this endpoint has a live console caller that sends no
 *       idempotency key, and the window itself is a perfectly good natural key.</li>
 *   <li><b>Fail-closed</b> — the arbiter is this service's own Postgres inside the same
 *       transaction, not a separate key store. There is no outage mode in which the
 *       guard silently degrades to fail-open: if the DB is unreachable the open itself
 *       fails, and no period is created.</li>
 *   <li><b>Partial, on purpose</b> — re-opening the same window after the earlier period
 *       was CLOSED (a correction re-run) stays allowed; a full unique index would block
 *       it.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OpenSettlementPeriodUseCase {

    private final SettlementPeriodRepository periodRepository;

    @Transactional
    public PeriodView open(String tenantId, Instant from, Instant to) {
        SettlementPeriod period = SettlementPeriod.open(
                UUID.randomUUID().toString(), tenantId, from, to);
        try {
            SettlementPeriod saved = periodRepository.insertOpen(period);
            return PeriodView.summary(saved);
        } catch (DataIntegrityViolationException e) {
            // The V6 partial unique index already rejected the duplicate row — whether
            // this was a sequential replay or the loser of a concurrent race. No second
            // OPEN period exists, so no second payout can be folded at close.
            log.info("Duplicate OPEN settlement period blocked by unique index: "
                    + "tenantId={}, from={}, to={}", tenantId, from, to);
            throw new PeriodAlreadyOpenException(
                    "an OPEN settlement period already covers this window: from=" + from
                            + " to=" + to, e);
        }
    }
}
