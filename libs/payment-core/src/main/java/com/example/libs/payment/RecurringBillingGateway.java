package com.example.libs.payment;

/**
 * Optional capability — a PG adapter implements this <b>only</b> if it supports server-initiated
 * recurring (billing-key) charges (ADR-MONO-057). Kept separate from {@link PaymentGatewayPort}
 * so a verify-only vendor is not forced to expose a charge op it cannot honour, mirroring
 * {@link RefundablePaymentGateway} / {@link PaymentStatusReadPort}.
 *
 * <p><b>New trust class — server-initiated, no client step.</b> Unlike
 * {@link PaymentGatewayPort#verify}, which always follows a client-initiated payment window, a
 * billing-key charge is triggered entirely server-side (e.g. a scheduler) against a previously
 * issued opaque {@code billingKey}. There is no browser, no user interaction, and no client
 * success signal to verify against — this op <b>moves money</b> itself.
 *
 * <p><b>FAILURE CONTRACT — why this differs from {@code verify}'s never-throws contract.</b>
 * {@link PaymentGatewayPort#verify} is a <b>read</b>: any failure fail-closes to
 * {@link PaymentAuthorization#declined()}, because a payment that cannot be confirmed PAID is
 * unambiguously safe to treat as not-paid. {@link #chargeBillingKey} is a <b>write</b> that
 * captures money, so a lost/errored response is <b>ambiguous</b> — the charge may have already
 * succeeded at the PG even though the caller never got a definitive answer. Silently returning
 * {@code declined()} for that ambiguous case would be a money-safety defect: a caller could read
 * "declined" as "safe to retry" and double-charge. An implementation MUST therefore distinguish:
 * <ul>
 *   <li><b>Definitive PG rejection</b> (a clear PG-side "no money moved" answer — e.g. a 4xx for
 *       an invalid/revoked billing key or insufficient funds) → <b>return
 *       {@link PaymentAuthorization#declined()}</b>. This is safe: the PG stated unambiguously that
 *       no charge stands.</li>
 *   <li><b>Ambiguous outcome</b> (timeout, 5xx, network/connection error, or any response the
 *       adapter cannot parse into a definitive PAID/rejected verdict) → <b>throw
 *       {@link PgGatewayUnavailableException}</b>. The adapter MUST NOT return {@code declined()}
 *       here, and MUST NOT internally retry the charge with the same {@code paymentId} (that risks
 *       a double-charge if the first attempt actually captured). The caller is responsible for
 *       reconciling via the base {@link PaymentGatewayPort#verify}
 *       ({@code paymentId}, ...) — using the same {@code paymentId} this call was invoked with —
 *       before deciding what happened, exactly as the stranded-refund reconciler already does for
 *       Toss cancels (TASK-BE-438). A durability-backstop webhook, when it arrives, is likewise
 *       only a trigger to run that same {@code verify} reconciliation (ADR-MONO-057 §D3) — never a
 *       payload to trust directly.</li>
 * </ul>
 * Whatever the outcome, an implementation MUST NOT turn the ambiguous path fail-open: a charge
 * whose success cannot be proven must never surface as {@code approved}.
 */
public interface RecurringBillingGateway {

    /**
     * Server-initiated charge against a previously issued billing key. Generates no client
     * interaction.
     *
     * @param billingKey  the opaque, vendor-issued billing-key reference (a durable charge
     *                    capability registered by the client SDK at issuance time)
     * @param paymentId   the caller-generated PG payment reference for <b>this</b> charge — the
     *                    same reference the caller passes to {@link PaymentGatewayPort#verify} to
     *                    reconcile an ambiguous outcome
     * @param amountMinor the amount, in minor currency units, to charge
     * @param currency    the ISO currency code to charge in (e.g. {@code "KRW"})
     * @param orderName   a human-readable order/description label forwarded to the PG
     * @return an approved {@link PaymentAuthorization} when the PG confirms the charge captured; a
     *         declined one on a definitive PG rejection. On an <b>ambiguous</b> outcome the
     *         implementation throws {@link PgGatewayUnavailableException} instead of returning —
     *         see the type-level FAILURE CONTRACT.
     */
    PaymentAuthorization chargeBillingKey(
            String billingKey, String paymentId, long amountMinor, String currency, String orderName);
}
