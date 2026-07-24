package com.example.libs.payment;

/**
 * Canonical, project-agnostic payment-gateway boundary (ADR-MONO-056 Phase 1).
 *
 * <p><b>Contract — "prove this payment is real for this amount + currency".</b> This is
 * the single invariant every consumer of a PG actually needs, and the superset both a
 * verify-model vendor (PortOne — the PG already captured; the adapter only reads back and
 * checks status/amount/currency) and a confirm-model vendor (Toss — the adapter performs
 * {@code POST /v1/payments/confirm}, which <b>captures money</b>, and returns the verified
 * result) can express behind one operation. The port therefore exposes <b>no</b> separate
 * capture op — a confirm-model adapter's capture happens <b>inside</b> {@link #verify}.
 *
 * <p><b>FAILURE CONTRACT (unification deferred to the consumer-migration tasks 479/480).</b>
 * The two relocated adapters diverge on how they signal a failed verification, and this port
 * deliberately admits <b>both</b> shapes so each adapter's existing money-safety behavior is
 * preserved 1:1 during Phase 1:
 * <ul>
 *   <li>An implementation MAY <b>return {@link PaymentAuthorization#declined()}</b> for a
 *       failed/forged/tampered/unreachable verification and never throw (the PortOne adapter's
 *       fail-closed-to-declined semantics — a PG outage yields a declined result, never an
 *       approved one).</li>
 *   <li>An implementation MAY instead <b>throw</b> a {@link PgConfirmFailedException}
 *       (permanent / PG-side rejection, e.g. 4xx) or a {@link PgGatewayUnavailableException}
 *       (transient / transport failure after resilience exhaustion) — the Toss adapter's
 *       classification, whose caller policy depends on distinguishing the two.</li>
 * </ul>
 * A consumer wiring a specific adapter must handle that adapter's declared failure shape.
 * Converging these two contracts into one is a behavior-preserving concern of TASK-MONO-479
 * (membership) / TASK-MONO-480 (payment), not of this extraction.
 *
 * <p>Whatever the outcome, an implementation MUST NOT turn a fail-closed path fail-open: a
 * verification that cannot be proven real must never surface as {@code approved}.
 */
public interface PaymentGatewayPort {

    /**
     * Verify (and, for a confirm-model vendor, capture) the payment described by
     * {@code request}.
     *
     * @param request the payment reference + expected amount/currency (+ optional order
     *                reference) to prove against the PG
     * @return an approved {@link PaymentAuthorization} when the PG proves the payment real for
     *         the expected amount and currency; a declined one when an implementation encodes
     *         failure in the return type. An implementation MAY alternatively throw a
     *         {@link PgConfirmFailedException} / {@link PgGatewayUnavailableException} — see the
     *         type-level FAILURE CONTRACT.
     */
    PaymentAuthorization verify(PaymentVerificationRequest request);
}
