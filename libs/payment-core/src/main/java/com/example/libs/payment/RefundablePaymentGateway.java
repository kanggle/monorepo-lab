package com.example.libs.payment;

/**
 * Optional capability — a PG adapter implements this <b>only</b> if it supports reversing a
 * captured payment. Kept separate from {@link PaymentGatewayPort} so a verify-only vendor is
 * not forced to expose a refund op it cannot honour.
 */
public interface RefundablePaymentGateway {

    /**
     * Full reversal of the captured payment at the PG.
     *
     * @param vendorPaymentRef the durable PG reference (e.g. Toss {@code paymentKey})
     * @param reason           human-readable cancel reason forwarded to the PG
     */
    void refund(String vendorPaymentRef, String reason);

    /**
     * Partial (or full) reversal of {@code amountMinor} minor units at the PG.
     *
     * @param vendorPaymentRef the durable PG reference
     * @param reason           human-readable cancel reason forwarded to the PG
     * @param amountMinor      the amount, in minor currency units, to reverse
     */
    void refund(String vendorPaymentRef, String reason, long amountMinor);
}
