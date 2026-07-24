package com.example.fanplatform.membership.infrastructure.payment;

import com.example.common.id.UuidV7;
import com.example.fanplatform.membership.domain.payment.PaymentGatewayPort;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Deterministic PG mock — the DEFAULT payment adapter (architecture.md § PG
 * Boundary). Here {@code paymentReference} is an opaque token:
 *
 * <ul>
 *   <li>{@code paymentReference == "tok_decline"} → declined.</li>
 *   <li>any other value (incl. null) → approved with
 *       {@code paymentRef = "pgmock_<uuid>"}.</li>
 * </ul>
 *
 * <p>{@code @Profile("!portone")} makes this the adapter for every environment
 * that does NOT set the {@code portone} profile — CI, integration tests, and
 * keyless local runs — so a real PG call never happens without explicit opt-in.
 * When the {@code portone} profile IS active, this bean is absent and
 * {@link PortOnePaymentAdapter} is the sole {@link PaymentGatewayPort}. (Profile
 * gating is used rather than {@code @ConditionalOnMissingBean}, which is
 * unreliable on component-scanned {@code @Component} classes.)
 */
@Component
@Profile("!portone")
public class MockPaymentGatewayAdapter implements PaymentGatewayPort {

    /** Reserved sentinel token that forces a decline (documented test boundary). */
    public static final String DECLINE_TOKEN = "tok_decline";

    private static final String PAYMENT_REF_PREFIX = "pgmock_";

    @Override
    public PaymentResult authorize(long amountMinor, int planMonths, String paymentReference, String idempotencyKey) {
        if (DECLINE_TOKEN.equals(paymentReference)) {
            return PaymentResult.declined();
        }
        return PaymentResult.approved(PAYMENT_REF_PREFIX + UuidV7.randomString());
    }
}
