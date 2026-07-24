package com.example.libs.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PaymentAuthorization canonical result factories")
class PaymentAuthorizationTest {

    @Test
    @DisplayName("approved() carries the vendor ref + optional method/receipt and is approved")
    void approvedFactory() {
        PaymentAuthorization a = PaymentAuthorization.approved("pk_123", "CARD", "https://receipt/abc");

        assertThat(a.approved()).isTrue();
        assertThat(a.vendorPaymentRef()).isEqualTo("pk_123");
        assertThat(a.paymentMethod()).isEqualTo("CARD");
        assertThat(a.receiptUrl()).isEqualTo("https://receipt/abc");
    }

    @Test
    @DisplayName("approved() permits null method/receipt (verify-model vendors leave them null)")
    void approvedFactoryAllowsNullMethodAndReceipt() {
        PaymentAuthorization a = PaymentAuthorization.approved("pay-abc", null, null);

        assertThat(a.approved()).isTrue();
        assertThat(a.vendorPaymentRef()).isEqualTo("pay-abc");
        assertThat(a.paymentMethod()).isNull();
        assertThat(a.receiptUrl()).isNull();
    }

    @Test
    @DisplayName("declined() is the fail-closed result — not approved, no reference")
    void declinedFactory() {
        PaymentAuthorization a = PaymentAuthorization.declined();

        assertThat(a.approved()).isFalse();
        assertThat(a.vendorPaymentRef()).isNull();
        assertThat(a.paymentMethod()).isNull();
        assertThat(a.receiptUrl()).isNull();
    }
}
