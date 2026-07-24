package com.example.libs.payment;

/**
 * Optional capability — a read-only PG state lookup, implemented <b>only</b> by adapters whose
 * vendor exposes a payment-status endpoint. Used by a double-refund guard: check the PG-side
 * state before re-issuing a reversal.
 */
public interface PaymentStatusReadPort {

    /**
     * Look up the coarse PG-side state of a payment.
     *
     * @param vendorPaymentRef the durable PG reference (e.g. Toss {@code paymentKey})
     * @return the mapped {@link PaymentGatewayStatus}. An implementation MAY throw a
     *         {@link PgConfirmFailedException} / {@link PgGatewayUnavailableException} on a read
     *         error rather than inferring resolution from it.
     */
    PaymentGatewayStatus fetchStatus(String vendorPaymentRef);
}
