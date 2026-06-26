package com.example.payment.application.port.out;

import com.example.payment.domain.model.StrandedRefund;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for {@link StrandedRefund} records (TASK-BE-438).
 *
 * <p>Operational / tenant-agnostic: the sweeper reconciles stranded refunds globally (like
 * order-service's {@code OrderStuckDetector}), so reads are not tenant-scoped.
 */
public interface StrandedRefundRepository {

    StrandedRefund save(StrandedRefund strandedRefund);

    Optional<StrandedRefund> findById(Long id);

    /** The single open ({@code STRANDED}) obligation for a payment, if any (dedupe at stranding time). */
    Optional<StrandedRefund> findOpenByPaymentId(String paymentId);

    /**
     * Due open obligations: {@code status = STRANDED AND next_attempt_at <= now}, oldest first,
     * capped at {@code limit}. Terminal rows are excluded by the status filter.
     */
    List<StrandedRefund> findDue(Instant now, int limit);

    /** Current count of open ({@code STRANDED}) obligations — backs the {@code payment_refund_stranded_open} gauge. */
    long countOpen();
}
