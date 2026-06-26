package com.example.payment.application.port.out;

import com.example.payment.domain.model.Payment;

import java.util.Optional;

public interface PaymentRepository {
    Payment save(Payment payment);
    Optional<Payment> findById(String paymentId);
    Optional<Payment> findByOrderId(String orderId);

    /**
     * Fresh, persistence-context-bypassing lookup by order id (TASK-BE-443, money-safety).
     *
     * <p>Unlike {@link #findByOrderId}, this returns the <b>committed database state</b> even
     * when the row is already managed in the caller's persistence context (L1 / first-level
     * cache). A plain derived query re-hydrates a matched row through the session, so an
     * already-managed entity's stale field values win over the freshly-read columns (Hibernate
     * session-level repeatable-read / managed-entity identity) — this method forces a DB refresh
     * so a concurrently-committed transition (e.g. an {@code OrderCancelled} that commits
     * {@code VOIDED} during a slow PG capture) is actually observed.
     *
     * <p>Used by {@code PaymentConfirmService}'s post-capture re-read so the money-safety
     * auto-refund / stranded-escalation path can detect a VOIDED-during-capture race that the
     * cached {@link #findByOrderId} would miss.
     */
    Optional<Payment> findByOrderIdFresh(String orderId);
}
