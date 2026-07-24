package com.example.libs.payment;

/**
 * Domain-free input to {@link PaymentGatewayPort#verify}. Carries only money / reference
 * primitives — no domain type (no Membership, no Order, no plan/subscription shape).
 *
 * @param paymentReference    the client-supplied PG reference to verify (PortOne
 *                            {@code paymentId}; Toss {@code paymentKey}). Required.
 * @param expectedAmountMinor the amount, in minor currency units, the PG-side payment must
 *                            equal (tamper guard)
 * @param currency            the ISO currency code the PG-side payment must be in (e.g.
 *                            {@code "KRW"})
 * @param orderReference      the vendor order reference — required by confirm-model vendors
 *                            (Toss {@code orderId}) and <b>nullable</b> for verify-model
 *                            vendors (PortOne ignores it)
 */
public record PaymentVerificationRequest(
        String paymentReference,
        long expectedAmountMinor,
        String currency,
        String orderReference) {
}
