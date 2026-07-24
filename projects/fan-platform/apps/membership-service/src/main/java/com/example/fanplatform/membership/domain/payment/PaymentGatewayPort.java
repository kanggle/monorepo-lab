package com.example.fanplatform.membership.domain.payment;

/**
 * The ONLY boundary to payment. Use cases depend on this port. Two adapters
 * implement it, selected by profile (architecture.md § PG Boundary): the default
 * {@code MockPaymentGatewayAdapter} ({@code @Profile("!portone")}) and the real
 * {@code PortOnePaymentAdapter} ({@code @Profile("portone")}). The domain and
 * use-case layers are unchanged by which adapter is active.
 */
public interface PaymentGatewayPort {

    /**
     * Authorizes (mock) or verifies (real PG) a subscription payment.
     *
     * @param amountMinor      the charge amount in minor units — the real adapter
     *                         verifies the PG-side paid amount equals this (tamper guard)
     * @param planMonths       subscription length in whole months
     * @param paymentReference client-supplied PG reference. Mock: an opaque token
     *                         (the reserved sentinel {@code tok_decline} forces a
     *                         decline). PortOne: the {@code paymentId} the client
     *                         obtained from the payment window, verified server-side.
     * @param idempotencyKey   the subscribe idempotency key (mock is stateless; the
     *                         real adapter may forward it to the PG)
     * @return the authorization/verification result
     */
    PaymentResult authorize(long amountMinor, int planMonths, String paymentReference, String idempotencyKey);

    /**
     * @param approved   true when the PG approved the charge
     * @param paymentRef  the PG authorization reference (e.g. {@code pgmock_<uuid>});
     *                    null when declined
     */
    record PaymentResult(boolean approved, String paymentRef) {

        public static PaymentResult approved(String paymentRef) {
            return new PaymentResult(true, paymentRef);
        }

        public static PaymentResult declined() {
            return new PaymentResult(false, null);
        }
    }
}
