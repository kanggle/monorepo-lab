package com.example.fanplatform.membership.infrastructure.payment;

import com.example.common.id.UuidV7;
import com.example.fanplatform.membership.domain.payment.PaymentGatewayPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

/**
 * Deterministic PG mock — there is NO real external PG integration in v1
 * (architecture.md § PG Mock Boundary). No real PG SDK.
 *
 * <ul>
 *   <li>{@code paymentToken == "tok_decline"} → declined.</li>
 *   <li>any other token (incl. null) → approved with
 *       {@code paymentRef = "pgmock_<uuid>"}.</li>
 * </ul>
 *
 * <p>Registered via {@link ConditionalOnMissingBean} so a future real
 * {@link PaymentGatewayPort} adapter (profile/condition) can replace it without
 * touching the domain or use-case layers.
 */
@Component
@ConditionalOnMissingBean(PaymentGatewayPort.class)
public class MockPaymentGatewayAdapter implements PaymentGatewayPort {

    /** Reserved sentinel token that forces a decline (documented test boundary). */
    public static final String DECLINE_TOKEN = "tok_decline";

    private static final String PAYMENT_REF_PREFIX = "pgmock_";

    @Override
    public PaymentResult authorize(long amountMinor, int planMonths, String paymentToken, String idempotencyKey) {
        if (DECLINE_TOKEN.equals(paymentToken)) {
            return PaymentResult.declined();
        }
        return PaymentResult.approved(PAYMENT_REF_PREFIX + UuidV7.randomString());
    }
}
