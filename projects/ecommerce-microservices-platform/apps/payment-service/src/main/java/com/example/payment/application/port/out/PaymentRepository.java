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

    /**
     * Cross-tenant existence check by {@code orderId} (TASK-BE-543 AC-1), deliberately
     * NOT scoped to the caller's tenant. Used only to detect — before an insert — that
     * a payment for this {@code orderId} already exists under a DIFFERENT tenant, so
     * the caller can reject with 404 instead of letting the write reach the global
     * {@code payments.order_id UNIQUE} constraint (which would surface as a 409 that
     * leaks cross-tenant existence, M3). Not a substitute for {@link #findByOrderId}'s
     * own-tenant idempotency/ownership decision.
     *
     * <p><b>This check alone is not a guard.</b> It is a read-then-write that two
     * concurrent requests both pass; the arbiter for the concurrent case is the global
     * {@code payments.order_id UNIQUE} constraint, which is why the create path must use
     * {@link #saveAndFlush} and translate the violation itself.
     */
    boolean existsByOrderIdAcrossTenants(String orderId);

    /**
     * Like {@link #save} but forces the INSERT synchronously (TASK-BE-543 AC-1).
     *
     * <p>{@code Payment} has an assigned {@code @Id} (no {@code @GeneratedValue}), so a
     * plain {@code save()} queues the INSERT until the commit-time flush — which happens
     * <em>after</em> the caller's try/catch and after the controller, making any
     * {@code DataIntegrityViolationException} catch around it dead code (the failure mode
     * catalogued by {@code TASK-BE-541}). A caller that needs to translate a
     * {@code payments.order_id} unique violation into a domain response must use this
     * method, not {@link #save}.
     */
    Payment saveAndFlush(Payment payment);
}
