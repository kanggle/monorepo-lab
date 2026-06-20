package com.example.batch.application;

import com.example.batch.domain.model.BatchJobExecution;
import com.example.batch.domain.repository.BatchJobExecutionRepository;
import com.example.batch.infrastructure.client.OrderServiceClient;
import com.example.batch.infrastructure.client.OrderServiceClient.ConfirmPaidStaleResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Job — forward-confirm paid-but-unconfirmed stale PENDING orders (TASK-BE-413 / AC-1..AC-5).
 *
 * <p><b>What it does:</b> Calls order-service
 * {@code POST /api/internal/orders/confirm-paid-stale} with a {@code client_credentials}
 * Bearer token. order-service evaluates the predicate and performs the transition
 * ({@code PENDING → CONFIRMED}) server-side. batch-worker only triggers the sweep and records
 * the outcome.
 *
 * <p><b>Failure isolation (AC-3):</b> any exception from {@link OrderServiceClient} (network
 * error, 4xx/5xx, token acquisition failure) is caught, a {@code FAILED} history row is
 * recorded with the error reason, and the exception is swallowed so that the scheduler thread
 * is never terminated by a job failure (overview.md invariant 2, mirrors BE-409).
 *
 * <p><b>ShedLock bypass:</b> tests call {@link #execute()} directly — never via the
 * scheduler — to avoid the {@code lockAtLeastFor} trap that silently no-ops subsequent
 * invocations within the lock window.
 *
 * <p><b>Metrics (AC-5):</b>
 * <ul>
 *   <li>{@code batch_paid_orders_confirmed_total} — incremented by {@code confirmed} tally</li>
 *   <li>{@code batch_paid_orders_confirm_skipped_total} — incremented by {@code skipped} tally</li>
 * </ul>
 */
@Slf4j
@Service
public class StalePaidOrderConfirmationJob {

    static final String JOB_NAME = "stalePaidOrderConfirmationJob";
    static final String CONFIRMED_COUNTER_NAME = "batch_paid_orders_confirmed_total";
    static final String SKIPPED_COUNTER_NAME = "batch_paid_orders_confirm_skipped_total";

    private final OrderServiceClient orderServiceClient;
    private final BatchJobExecutionRepository executionRepository;
    private final Counter confirmedCounter;
    private final Counter skippedCounter;

    @Value("${batch.jobs.stale-paid-order-confirmation.enabled:true}")
    private boolean enabled;

    public StalePaidOrderConfirmationJob(
            OrderServiceClient orderServiceClient,
            BatchJobExecutionRepository executionRepository,
            MeterRegistry meterRegistry) {
        this.orderServiceClient = orderServiceClient;
        this.executionRepository = executionRepository;
        this.confirmedCounter = meterRegistry.counter(CONFIRMED_COUNTER_NAME);
        this.skippedCounter = meterRegistry.counter(SKIPPED_COUNTER_NAME);
    }

    /**
     * Execute one forward-confirm sweep pass.
     *
     * <p>History lifecycle: {@code RUNNING → COMPLETED} (with tally in log) on success,
     * {@code RUNNING → FAILED} on any error. Exceptions are swallowed after recording FAILED
     * history so the scheduler thread is never terminated (overview.md invariant 2).
     */
    public void execute() {
        if (!enabled) {
            log.info("stalePaidOrderConfirmationJob is disabled via " +
                    "batch.jobs.stale-paid-order-confirmation.enabled=false; skipping.");
            return;
        }

        BatchJobExecution execution = executionRepository.save(BatchJobExecution.start(JOB_NAME));
        log.info("StalePaidOrderConfirmationJob started (executionId={})", execution.getId());

        try {
            ConfirmPaidStaleResponse response = orderServiceClient.confirmPaidStale();

            // Increment metrics by the tally returned from order-service (AC-5)
            if (response.confirmed() > 0) {
                confirmedCounter.increment(response.confirmed());
            }
            if (response.skipped() > 0) {
                skippedCounter.increment(response.skipped());
            }

            execution.complete();
            executionRepository.save(execution);
            log.info("StalePaidOrderConfirmationJob completed (executionId={} scanned={} confirmed={} skipped={})",
                    execution.getId(), response.scanned(), response.confirmed(), response.skipped());

        } catch (Exception e) {
            // Guard for blank message — e.getMessage() may be non-null but "" (blank),
            // which would cause BatchJobExecution.fail() to throw IllegalArgumentException,
            // escaping this catch block and breaking exception isolation (BE-409 pattern).
            String errorMsg = (e.getMessage() != null && !e.getMessage().isBlank())
                    ? e.getMessage()
                    : e.getClass().getName();
            execution.fail(errorMsg);
            executionRepository.save(execution);
            log.error("StalePaidOrderConfirmationJob FAILED (executionId={}): {}",
                    execution.getId(), errorMsg, e);
            // Do NOT re-throw — failed jobs must not block the scheduler thread (overview.md invariant 2).
        }
    }
}
