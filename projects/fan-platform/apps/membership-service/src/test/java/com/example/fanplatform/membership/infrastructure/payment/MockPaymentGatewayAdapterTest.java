package com.example.fanplatform.membership.infrastructure.payment;

import com.example.libs.payment.PaymentAuthorization;
import com.example.libs.payment.PaymentVerificationRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentGatewayAdapterTest {

    private final MockPaymentGatewayAdapter adapter = new MockPaymentGatewayAdapter();

    /** The mock keys only off the payment reference; amount/currency/order are inert. */
    private static PaymentVerificationRequest req(String paymentReference) {
        return new PaymentVerificationRequest(paymentReference, 9900L, "KRW", null);
    }

    @Test
    @DisplayName("tok_decline → declined, no vendorPaymentRef")
    void declineToken() {
        PaymentAuthorization result = adapter.verify(req(MockPaymentGatewayAdapter.DECLINE_TOKEN));
        assertThat(result.approved()).isFalse();
        assertThat(result.vendorPaymentRef()).isNull();
    }

    @Test
    @DisplayName("any other token → approved with pgmock_ vendorPaymentRef")
    void approveToken() {
        PaymentAuthorization result = adapter.verify(req("tok_visa_demo"));
        assertThat(result.approved()).isTrue();
        assertThat(result.vendorPaymentRef()).startsWith("pgmock_");
    }

    @Test
    @DisplayName("null token → approved (default)")
    void nullTokenApproves() {
        PaymentAuthorization result = adapter.verify(req(null));
        assertThat(result.approved()).isTrue();
        assertThat(result.vendorPaymentRef()).startsWith("pgmock_");
    }
}
