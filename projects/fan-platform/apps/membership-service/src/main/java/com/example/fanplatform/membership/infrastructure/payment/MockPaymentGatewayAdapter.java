package com.example.fanplatform.membership.infrastructure.payment;

import com.example.common.id.UuidV7;
import com.example.libs.payment.PaymentAuthorization;
import com.example.libs.payment.PaymentGatewayPort;
import com.example.libs.payment.PaymentVerificationRequest;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Deterministic PG mock — the DEFAULT payment adapter (architecture.md § PG
 * Boundary), implementing the shared {@link PaymentGatewayPort} (ADR-MONO-056).
 * Here {@link PaymentVerificationRequest#paymentReference()} is an opaque token:
 *
 * <ul>
 *   <li>{@code paymentReference == "tok_decline"} → declined.</li>
 *   <li>any other value (incl. null) → approved with
 *       {@code vendorPaymentRef = "pgmock_<uuid>"}.</li>
 * </ul>
 *
 * <p>Stateless and deterministic: it ignores the expected amount / currency /
 * order reference on the request (they are only meaningful to a real PG that
 * verifies against a captured payment). Fail-closed by return value, never by
 * throwing — mirroring the PortOne adapter's declined-not-thrown contract.
 *
 * <p>{@code @Profile("!portone")} makes this the adapter for every environment
 * that does NOT set the {@code portone} profile — CI, integration tests, and
 * keyless local runs — so a real PG call never happens without explicit opt-in.
 * When the {@code portone} profile IS active, this bean is absent and the lib
 * {@code PortOnePaymentAdapter} (wired by {@link PaymentGatewayConfig}) is the
 * sole {@link PaymentGatewayPort}. (Profile gating is used rather than
 * {@code @ConditionalOnMissingBean}, which is unreliable on component-scanned
 * {@code @Component} classes.)
 */
@Component
@Profile("!portone")
public class MockPaymentGatewayAdapter implements PaymentGatewayPort {

    /** Reserved sentinel token that forces a decline (documented test boundary). */
    public static final String DECLINE_TOKEN = "tok_decline";

    private static final String PAYMENT_REF_PREFIX = "pgmock_";

    @Override
    public PaymentAuthorization verify(PaymentVerificationRequest request) {
        if (DECLINE_TOKEN.equals(request.paymentReference())) {
            return PaymentAuthorization.declined();
        }
        return PaymentAuthorization.approved(PAYMENT_REF_PREFIX + UuidV7.randomString(), null, null);
    }
}
