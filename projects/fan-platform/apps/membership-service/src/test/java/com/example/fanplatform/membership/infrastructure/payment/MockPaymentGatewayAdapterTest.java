package com.example.fanplatform.membership.infrastructure.payment;

import com.example.fanplatform.membership.domain.payment.PaymentGatewayPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MockPaymentGatewayAdapterTest {

    private final MockPaymentGatewayAdapter adapter = new MockPaymentGatewayAdapter();

    @Test
    @DisplayName("tok_decline → declined, no paymentRef")
    void declineToken() {
        PaymentGatewayPort.PaymentResult result =
                adapter.authorize(9900, 1, MockPaymentGatewayAdapter.DECLINE_TOKEN, "idem-1");
        assertThat(result.approved()).isFalse();
        assertThat(result.paymentRef()).isNull();
    }

    @Test
    @DisplayName("any other token → approved with pgmock_ paymentRef")
    void approveToken() {
        PaymentGatewayPort.PaymentResult result =
                adapter.authorize(9900, 1, "tok_visa_demo", "idem-2");
        assertThat(result.approved()).isTrue();
        assertThat(result.paymentRef()).startsWith("pgmock_");
    }

    @Test
    @DisplayName("null token → approved (default)")
    void nullTokenApproves() {
        PaymentGatewayPort.PaymentResult result = adapter.authorize(9900, 1, null, "idem-3");
        assertThat(result.approved()).isTrue();
        assertThat(result.paymentRef()).startsWith("pgmock_");
    }
}
