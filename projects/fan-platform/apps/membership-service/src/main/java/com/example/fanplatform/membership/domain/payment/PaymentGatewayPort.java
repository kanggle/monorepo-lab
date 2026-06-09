package com.example.fanplatform.membership.domain.payment;

/**
 * The ONLY boundary to payment authorization. Use cases depend on this port; the
 * v1 implementation is a deterministic mock
 * ({@code infrastructure.payment.MockPaymentGatewayAdapter}). A real PG adapter is
 * a future increment that re-implements this port — the domain and use-case
 * layers are unchanged by that swap.
 */
public interface PaymentGatewayPort {

    /**
     * Authorizes a subscription payment.
     *
     * @param amountMinor    the charge amount in minor units (not persisted in v1)
     * @param planMonths     subscription length in whole months
     * @param paymentToken   client-supplied opaque token (the reserved sentinel
     *                       {@code tok_decline} forces a decline)
     * @param idempotencyKey the subscribe idempotency key (mock is stateless but
     *                       the real adapter forwards it to the PG)
     * @return the authorization result
     */
    PaymentResult authorize(long amountMinor, int planMonths, String paymentToken, String idempotencyKey);

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
