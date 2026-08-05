package com.example.payment.config;

import com.example.libs.payment.PaymentAuthorization;
import com.example.libs.payment.PaymentGatewayStatus;
import com.example.libs.payment.PaymentVerificationRequest;
import com.example.libs.payment.PgConfirmFailedException;
import com.example.payment.config.DemoPaymentGatewayConfig.DemoPaymentGateway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("demo-pg mock 게이트웨이 (TASK-BE-572)")
class DemoPaymentGatewayTest {

    private final DemoPaymentGateway gateway = new DemoPaymentGateway();

    private static PaymentVerificationRequest request(String paymentRef, String orderRef) {
        return new PaymentVerificationRequest(paymentRef, 12_900L, "KRW", orderRef);
    }

    @Test
    @DisplayName("승인 — vendorPaymentRef 는 호출자가 준 paymentReference 를 그대로 돌려준다")
    void verify_approves_echoingPaymentReference() {
        PaymentAuthorization auth = gateway.verify(request("demo_pk_1", "order-1"));

        assertThat(auth.approved()).isTrue();
        assertThat(auth.vendorPaymentRef()).isEqualTo("demo_pk_1");
        assertThat(auth.paymentMethod()).isEqualTo("CARD");
    }

    @Test
    @DisplayName("paymentReference 가 null 이어도 vendorPaymentRef 를 만들어 낸다 (null 저장 금지)")
    void verify_nullPaymentReference_stillYieldsAVendorRef() {
        PaymentAuthorization auth = gateway.verify(request(null, "order-2"));

        assertThat(auth.approved()).isTrue();
        assertThat(auth.vendorPaymentRef()).isEqualTo("demopg_order-2");
    }

    /**
     * The decline path must THROW, not return {@code declined()}.
     *
     * <p>This is asserted rather than left to the class comment because it is the whole reason the
     * mock is shaped the way it is: {@code PaymentConfirmService.confirm} never reads
     * {@code PaymentAuthorization.approved()}, so a mock that returned {@code declined()} would be
     * recorded as a <em>successful</em> payment. If someone later "simplifies" this to
     * {@code declined()}, this test is what stops it.
     */
    @Test
    @DisplayName("거절은 declined() 반환이 아니라 PgConfirmFailedException 을 던진다")
    void verify_declineSentinel_throwsInsteadOfReturningDeclined() {
        assertThatThrownBy(() -> gateway.verify(request("demo_pk_3", DemoPaymentGateway.DECLINE_ORDER_REFERENCE)))
                .isInstanceOf(PgConfirmFailedException.class);
    }

    @Test
    @DisplayName("센티널은 orderReference 에만 반응한다 — 같은 값이 paymentReference 에 와도 승인")
    void verify_sentinelOnPaymentReference_isNotADecline() {
        PaymentAuthorization auth =
                gateway.verify(request(DemoPaymentGateway.DECLINE_ORDER_REFERENCE, "order-4"));

        assertThat(auth.approved()).isTrue();
    }

    @Test
    @DisplayName("환불 두 형태 모두 던지지 않는다 — confirm 의 post-capture 자동환불 경로가 이걸 부른다")
    void refund_bothOverloads_doNotThrow() {
        assertThatCode(() -> gateway.refund("demopg_x", "reason")).doesNotThrowAnyException();
        assertThatCode(() -> gateway.refund("demopg_x", "reason", 1_000L)).doesNotThrowAnyException();
    }

    /**
     * The stranded-refund sweeper is {@code @Profile("!standalone")}, so unlike the standalone
     * stub this gateway IS polled under {@code demo-pg} and must answer.
     */
    @Test
    @DisplayName("fetchStatus 는 CAPTURED — 스위퍼가 demo-pg 에서 실제로 돈다")
    void fetchStatus_isCaptured() {
        assertThat(gateway.fetchStatus("demopg_x")).isEqualTo(PaymentGatewayStatus.CAPTURED);
    }
}
