package com.example.payment.application.service;

import com.example.payment.application.event.PaymentRefundUnresolvedEvent;
import com.example.payment.application.exception.PgConfirmFailedException;
import com.example.payment.application.exception.PgGatewayUnavailableException;
import com.example.payment.application.port.out.PaymentEventPublisher;
import com.example.payment.application.port.out.PaymentGatewayPort;
import com.example.payment.application.port.out.PaymentGatewayStatus;
import com.example.payment.application.port.out.PaymentMetricRecorder;
import com.example.payment.application.port.out.StrandedRefundRepository;
import com.example.payment.domain.model.StrandedRefund;
import com.example.payment.domain.tenant.TenantContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Per-record stranded-refund reconciler (TASK-BE-438, ADR-MONO-005 § 2.3 D3 Category A).
 *
 * <p>Lives on a <b>separate bean</b> from {@link StrandedRefundSweeper} so the {@code REQUIRES_NEW}
 * {@code @Transactional} call is intercepted by the Spring AOP proxy. Self-invocation from the
 * sweeper would bypass the proxy and silently downgrade to the calling thread's TX — one poisoned
 * record would then roll back the whole batch (AC-6 / F5). Mirrors order-service's
 * {@code OrderStuckRecoveryHandler} and wms {@code SagaRecoveryHandler} split.
 *
 * <p><b>PG-state-first (double-refund guard, F1).</b> A transient stranding ({@code 5xx}/circuit-open
 * cancel) may have actually cancelled at the PG. The reconciler therefore reads the PG state
 * <b>before</b> re-issuing a cancel:
 * <ul>
 *   <li>{@link PaymentGatewayStatus#CANCELED} — already reversed → {@code RESOLVED}, no second cancel.</li>
 *   <li>{@link PaymentGatewayStatus#CAPTURED} — still held → re-issue {@code cancelPayment}. On
 *       success → {@code RESOLVED}; on transient {@code PgGatewayUnavailableException} → backoff;
 *       on definitive {@code PgConfirmFailedException} (4xx) → terminal {@code UNRESOLVED}.</li>
 *   <li>{@link PaymentGatewayStatus#UNKNOWN} or any fetch failure — treat as transient (backoff);
 *       never infer resolution from an error.</li>
 * </ul>
 *
 * <p><b>Bounded retry → terminal (F2/F3).</b> A transient failure increments {@code attempts} and
 * pushes {@code next_attempt_at} out by exponential backoff (1s → 2s → 4s … capped — the
 * {@code AbstractOutboxPublisher} shape). At {@code attempts >= max-attempts} the record transitions
 * to terminal {@code UNRESOLVED}, re-emits a {@code PaymentRefundUnresolved} escalation, and is never
 * auto-retried again. The terminal status transition and its escalation event co-commit in this
 * {@code REQUIRES_NEW} TX (all-or-nothing).
 */
@Slf4j
@Component
@Profile("!standalone")
public class StrandedRefundReconciler {

    static final String RECONCILE_CANCEL_REASON = "Stranded refund auto-reconciliation (TASK-BE-438)";

    private final StrandedRefundRepository repository;
    private final PaymentGatewayPort paymentGateway;
    private final PaymentEventPublisher paymentEventPublisher;
    private final PaymentMetricRecorder metrics;
    private final Clock clock;

    private final int maxAttempts;
    private final long initialBackoffMs;
    private final long maxBackoffMs;

    public StrandedRefundReconciler(StrandedRefundRepository repository,
                                    PaymentGatewayPort paymentGateway,
                                    PaymentEventPublisher paymentEventPublisher,
                                    PaymentMetricRecorder metrics,
                                    Clock clock,
                                    @Value("${payment.stranded-refund.max-attempts:8}") int maxAttempts,
                                    @Value("${payment.stranded-refund.initial-backoff-ms:1000}") long initialBackoffMs,
                                    @Value("${payment.stranded-refund.max-backoff-ms:30000}") long maxBackoffMs) {
        this.repository = repository;
        this.paymentGateway = paymentGateway;
        this.paymentEventPublisher = paymentEventPublisher;
        this.metrics = metrics;
        this.clock = clock;
        this.maxAttempts = Math.max(1, maxAttempts);
        this.initialBackoffMs = Math.max(1, initialBackoffMs);
        this.maxBackoffMs = Math.max(initialBackoffMs, maxBackoffMs);
    }

    /**
     * Reconcile a single stranded-refund record in its own {@code REQUIRES_NEW} transaction.
     * Any exception rolls back only this record's TX; the sweeper loop continues with the rest.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reconcile(Long id) {
        StrandedRefund record = repository.findById(id).orElse(null);
        if (record == null) {
            log.warn("stranded_refund_reconcile_vanished id={}", id);
            return;
        }
        // Re-check under fresh load — a concurrent tick or a terminal transition may have advanced it.
        if (!record.isOpen()) {
            log.debug("stranded_refund_reconcile_skip_terminal id={} status={}", id, record.getStatus());
            return;
        }

        Instant now = Instant.now(clock);
        String paymentKey = record.getPaymentKey();
        if (paymentKey == null) {
            // Nothing to reconcile at the PG without a payment key — terminal, needs an operator.
            terminate(record, now, "missing paymentKey — cannot reconcile at PG");
            return;
        }

        // ── PG-state-first (double-refund guard) ──────────────────────────────────────────────
        PaymentGatewayStatus state;
        try {
            state = paymentGateway.fetchStatus(paymentKey);
        } catch (PgGatewayUnavailableException | PgConfirmFailedException e) {
            // Fetch failure is transient — never infer resolution from a read error.
            backoffOrTerminate(record, now, "fetchStatus failed: " + e.getClass().getSimpleName());
            return;
        }

        switch (state) {
            case CANCELED -> resolve(record, now, "PG already CANCELED — original cancel succeeded");
            case CAPTURED -> recancel(record, now, paymentKey);
            case UNKNOWN -> backoffOrTerminate(record, now, "PG status UNKNOWN — cannot confirm cancel");
        }
    }

    private void recancel(StrandedRefund record, Instant now, String paymentKey) {
        try {
            paymentGateway.cancelPayment(paymentKey, RECONCILE_CANCEL_REASON);
            resolve(record, now, "retry cancel succeeded at PG");
        } catch (PgGatewayUnavailableException e) {
            // Transient transport failure — bounded retry with backoff.
            backoffOrTerminate(record, now, "cancel transient failure: " + e.getClass().getSimpleName());
        } catch (PgConfirmFailedException e) {
            // Definitive 4xx — the PG refuses the cancel; not auto-resolvable → straight to terminal.
            terminate(record, now, "cancel definitively rejected (4xx): " + e.getMessage());
        }
    }

    private void resolve(StrandedRefund record, Instant now, String detail) {
        record.markResolved(now);
        repository.save(record);
        metrics.incrementRefundStrandedResolved();
        log.info("stranded_refund_resolved id={} paymentId={} attempts={} detail={}",
                record.getId(), record.getPaymentId(), record.getAttempts(), detail);
    }

    /**
     * Apply a transient outcome: if this retry would reach the cap, terminate to {@code UNRESOLVED};
     * otherwise increment attempts, push {@code next_attempt_at} out by exponential backoff, and
     * stay {@code STRANDED} (AC-4 — the sweeper skips a record whose {@code next_attempt_at} is in
     * the future, so no retry storm).
     */
    private void backoffOrTerminate(StrandedRefund record, Instant now, String error) {
        if (record.wouldExhaust(maxAttempts)) {
            terminate(record, now, "attempt cap (" + maxAttempts + ") exhausted; last=" + error);
            return;
        }
        int nextAttempt = record.getAttempts() + 1;
        Instant nextAttemptAt = now.plus(Duration.ofMillis(backoffMillis(nextAttempt)));
        record.recordTransientFailure(now, nextAttemptAt, error);
        repository.save(record);
        log.warn("stranded_refund_retry id={} paymentId={} attempt={} nextAttemptAt={} error={}",
                record.getId(), record.getPaymentId(), nextAttempt, nextAttemptAt, error);
    }

    /**
     * Terminal {@code UNRESOLVED} (F3): stop auto-retrying, increment the unresolved metric, and
     * re-emit the {@code PaymentRefundUnresolved} escalation so an operator is paged. The status
     * save and the escalation event co-commit in this {@code REQUIRES_NEW} TX.
     */
    private void terminate(StrandedRefund record, Instant now, String error) {
        record.markUnresolved(now, error);
        repository.save(record);

        PaymentRefundUnresolvedEvent event = PaymentRefundUnresolvedEvent.of(
                record.getPaymentId(), record.getOrderId(), record.getPaymentKey(),
                record.getAmount(), record.getReason(), record.getAttempts(),
                error, TenantContext.currentTenant());
        paymentEventPublisher.publishPaymentRefundUnresolved(event);

        metrics.incrementRefundStrandedUnresolved();
        log.error("stranded_refund_unresolved id={} paymentId={} orderId={} amount={} attempts={} error={}",
                record.getId(), record.getPaymentId(), record.getOrderId(), record.getAmount(),
                record.getAttempts(), error);
    }

    /** Exponential backoff: {@code initial * 2^(attempt-1)}, capped — the AbstractOutboxPublisher shape. */
    long backoffMillis(int attempt) {
        long delay = (long) (initialBackoffMs * Math.pow(2.0, Math.max(0, attempt - 1)));
        return Math.min(delay, maxBackoffMs);
    }
}
