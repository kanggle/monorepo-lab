package com.example.libs.payment;

/**
 * Domain-free result of {@link PaymentGatewayPort#verify}. Carries only the verified PG
 * outcome — no domain type.
 *
 * @param approved         true when the PG proved the payment real for the expected amount and
 *                         currency
 * @param vendorPaymentRef the durable PG reference for the verified payment (PortOne uses the
 *                         {@code paymentId} itself; Toss uses the {@code paymentKey}); null when
 *                         declined
 * @param paymentMethod    the PG-reported payment method — <b>nullable</b>: a verify-model
 *                         vendor (PortOne) leaves it null; a confirm-model vendor (Toss) fills it
 * @param receiptUrl       the PG-reported receipt URL — <b>nullable</b>, same rationale as
 *                         {@code paymentMethod}
 */
public record PaymentAuthorization(
        boolean approved,
        String vendorPaymentRef,
        String paymentMethod,
        String receiptUrl) {

    /** An approved verification. {@code paymentMethod} / {@code receiptUrl} may be null. */
    public static PaymentAuthorization approved(String vendorPaymentRef, String paymentMethod, String receiptUrl) {
        return new PaymentAuthorization(true, vendorPaymentRef, paymentMethod, receiptUrl);
    }

    /** A declined verification — the fail-closed result (no reference, no method, no receipt). */
    public static PaymentAuthorization declined() {
        return new PaymentAuthorization(false, null, null, null);
    }
}
