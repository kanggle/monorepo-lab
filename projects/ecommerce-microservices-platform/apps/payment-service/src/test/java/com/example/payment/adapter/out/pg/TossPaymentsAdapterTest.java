package com.example.payment.adapter.out.pg;

import com.example.payment.application.exception.PgConfirmFailedException;
import com.example.payment.application.port.out.PaymentGatewayConfirmResult;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.web.client.RestClient;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("TossPaymentsAdapter WireMock 테스트")
class TossPaymentsAdapterTest {

    private static WireMockServer wireMock;
    private TossPaymentsAdapter adapter;

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMock.start();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @BeforeEach
    void setUp() {
        wireMock.resetAll();
        TossPaymentsProperties properties = new TossPaymentsProperties(
                "test_sk_secret", "http://localhost:" + wireMock.port()
        );
        adapter = new TossPaymentsAdapter(properties, RestClient.builder());
    }

    // --- confirmPayment ---

    @Test
    @DisplayName("PG 승인 성공 시 paymentMethod와 receiptUrl을 반환한다")
    void confirmPayment_success_returnsResult() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments/confirm"))
                .willReturn(okJson("""
                        {
                            "paymentKey": "pk_test_123",
                            "orderId": "order-1",
                            "status": "DONE",
                            "method": "CARD",
                            "receipt": {
                                "url": "https://receipt.tosspayments.com/abc"
                            }
                        }
                        """)));

        PaymentGatewayConfirmResult result = adapter.confirmPayment("pk_test_123", "order-1", 30000L);

        assertThat(result.paymentMethod()).isEqualTo("CARD");
        assertThat(result.receiptUrl()).isEqualTo("https://receipt.tosspayments.com/abc");

        wireMock.verify(postRequestedFor(urlEqualTo("/v1/payments/confirm"))
                .withHeader("Authorization", matching("Basic .*"))
                .withRequestBody(matchingJsonPath("$.paymentKey", equalTo("pk_test_123")))
                .withRequestBody(matchingJsonPath("$.orderId", equalTo("order-1")))
                .withRequestBody(matchingJsonPath("$.amount", equalTo("30000"))));
    }

    @Test
    @DisplayName("PG 승인 시 receipt 필드가 없으면 receiptUrl은 null이다")
    void confirmPayment_noReceipt_returnsNullReceiptUrl() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments/confirm"))
                .willReturn(okJson("""
                        {
                            "paymentKey": "pk_test_123",
                            "orderId": "order-1",
                            "status": "DONE",
                            "method": "TRANSFER"
                        }
                        """)));

        PaymentGatewayConfirmResult result = adapter.confirmPayment("pk_test_123", "order-1", 30000L);

        assertThat(result.paymentMethod()).isEqualTo("TRANSFER");
        assertThat(result.receiptUrl()).isNull();
    }

    @Test
    @DisplayName("PG 승인 4xx 에러 시 PgConfirmFailedException이 발생한다")
    void confirmPayment_clientError_throwsPgConfirmFailed() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments/confirm"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":"ALREADY_PROCESSED_PAYMENT","message":"이미 처리된 결제입니다."}
                                """)));

        assertThatThrownBy(() -> adapter.confirmPayment("pk_test_123", "order-1", 30000L))
                .isInstanceOf(PgConfirmFailedException.class);
    }

    @Test
    @DisplayName("PG 승인 5xx 에러 시 PgConfirmFailedException이 발생한다")
    void confirmPayment_serverError_throwsPgConfirmFailed() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments/confirm"))
                .willReturn(serverError()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":"INTERNAL_ERROR","message":"서버 내부 오류"}
                                """)));

        assertThatThrownBy(() -> adapter.confirmPayment("pk_test_123", "order-1", 30000L))
                .isInstanceOf(PgConfirmFailedException.class);
    }

    // --- cancelPayment ---

    @Test
    @DisplayName("PG 취소 성공 시 예외 없이 완료된다")
    void cancelPayment_success_completesWithoutException() {
        wireMock.stubFor(post(urlPathEqualTo("/v1/payments/pk_test_123/cancel"))
                .willReturn(okJson("""
                        {"paymentKey":"pk_test_123","status":"CANCELED"}
                        """)));

        adapter.cancelPayment("pk_test_123", "Order cancelled");

        wireMock.verify(postRequestedFor(urlPathEqualTo("/v1/payments/pk_test_123/cancel"))
                .withRequestBody(matchingJsonPath("$.cancelReason", equalTo("Order cancelled"))));
    }

    @Test
    @DisplayName("PG 취소 실패 시 PgConfirmFailedException이 발생한다")
    void cancelPayment_failure_throwsPgConfirmFailed() {
        wireMock.stubFor(post(urlPathEqualTo("/v1/payments/pk_test_123/cancel"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":"ALREADY_CANCELED_PAYMENT","message":"이미 취소된 결제입니다."}
                                """)));

        assertThatThrownBy(() -> adapter.cancelPayment("pk_test_123", "Order cancelled"))
                .isInstanceOf(PgConfirmFailedException.class);
    }
}
