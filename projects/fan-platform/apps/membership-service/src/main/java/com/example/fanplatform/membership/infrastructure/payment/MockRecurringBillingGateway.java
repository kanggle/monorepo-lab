package com.example.fanplatform.membership.infrastructure.payment;

import com.example.libs.payment.PaymentAuthorization;
import com.example.libs.payment.PaymentGatewayPort;
import com.example.libs.payment.RecurringBillingGateway;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Deterministic recurring-billing mock — the DEFAULT {@link RecurringBillingGateway}
 * for every non-{@code portone} environment (CI, integration tests, keyless local
 * runs), mirroring {@link MockPaymentGatewayAdapter}'s role for
 * {@link PaymentGatewayPort}. Without it the auto-renew wiring would have no
 * {@code RecurringBillingGateway} bean under the mock profile and the context would
 * fail to start.
 *
 * <ul>
 *   <li>{@code billingKey == "bkey_decline"} → declined (documented test boundary,
 *       parallel to {@code MockPaymentGatewayAdapter}'s {@code tok_decline}).</li>
 *   <li>any other value → approved with {@code vendorPaymentRef = paymentId} (the
 *       PortOne verify-model shape — the paymentId is the durable reference). The
 *       subsequent {@code RenewMembershipUseCase.verify(paymentId)} then approves
 *       under the mock, so a demo auto-renew flows end-to-end.</li>
 * </ul>
 *
 * <p>Never throws (no ambiguous path in the mock — the ambiguous
 * {@code PgGatewayUnavailableException} branch is exercised in unit tests with a
 * mocked gateway). Never logs the billing key.
 */
@Component
@Profile("!portone")
public class MockRecurringBillingGateway implements RecurringBillingGateway {

    /** Reserved sentinel billing key that forces a decline (documented test boundary). */
    public static final String DECLINE_BILLING_KEY = "bkey_decline";

    @Override
    public PaymentAuthorization chargeBillingKey(
            String billingKey, String paymentId, long amountMinor, String currency, String orderName) {
        if (DECLINE_BILLING_KEY.equals(billingKey)) {
            return PaymentAuthorization.declined();
        }
        return PaymentAuthorization.approved(paymentId, null, null);
    }
}
