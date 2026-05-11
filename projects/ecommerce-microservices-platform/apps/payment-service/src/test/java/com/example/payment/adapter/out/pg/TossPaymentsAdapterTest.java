package com.example.payment.adapter.out.pg;

import com.example.payment.application.exception.PgConfirmFailedException;
import com.example.payment.application.exception.PgGatewayUnavailableException;
import com.example.payment.application.port.out.PaymentGatewayConfirmResult;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.junit.jupiter.api.*;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;

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
                "test_sk_secret", "http://localhost:" + wireMock.port(), 5000, 10000
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
    @DisplayName("PG 승인 5xx 에러는 adapter 에서 catch 하지 않고 HttpServerErrorException 으로 전파된다 (ADR-MONO-005 § D4 — R4j retry-exceptions 가 받는다)")
    void confirmPayment_serverError_propagatesHttpServerErrorException() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments/confirm"))
                .willReturn(serverError()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":"INTERNAL_ERROR","message":"서버 내부 오류"}
                                """)));

        assertThatThrownBy(() -> adapter.confirmPayment("pk_test_123", "order-1", 30000L))
                .isInstanceOf(HttpServerErrorException.class);
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
    @DisplayName("PG 취소 4xx 실패 시 PgConfirmFailedException이 발생한다")
    void cancelPayment_clientError_throwsPgConfirmFailed() {
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

    @Test
    @DisplayName("PG 취소 5xx 는 adapter 에서 catch 하지 않고 HttpServerErrorException 으로 전파된다")
    void cancelPayment_serverError_propagatesHttpServerErrorException() {
        wireMock.stubFor(post(urlPathEqualTo("/v1/payments/pk_test_123/cancel"))
                .willReturn(serverError()));

        assertThatThrownBy(() -> adapter.cancelPayment("pk_test_123", "Order cancelled"))
                .isInstanceOf(HttpServerErrorException.class);
    }

    // --- Fallback method classification (ADR-MONO-005 § D4) ---

    @Test
    @DisplayName("confirmFallback 은 HttpServerErrorException cause 를 PgGatewayUnavailableException 으로 변환한다")
    void confirmFallback_serverError_translatesToGatewayUnavailable() {
        HttpServerErrorException cause = HttpServerErrorException.create(
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error", null, new byte[0], null);

        assertThatThrownBy(() -> adapter.confirmFallback("pk_test_123", "order-1", 30000L, cause))
                .isInstanceOf(PgGatewayUnavailableException.class)
                .hasMessageContaining("pk_test_123")
                .hasMessageContaining("HttpServerErrorException")
                .hasCause(cause);
    }

    @Test
    @DisplayName("confirmFallback 은 CallNotPermittedException (CB OPEN) 도 PgGatewayUnavailableException 으로 변환한다")
    void confirmFallback_circuitOpen_translatesToGatewayUnavailable() {
        CircuitBreaker cb = CircuitBreaker.of("toss-payments", CircuitBreakerConfig.ofDefaults());
        CallNotPermittedException cause = CallNotPermittedException.createCallNotPermittedException(cb);

        assertThatThrownBy(() -> adapter.confirmFallback("pk_test_123", "order-1", 30000L, cause))
                .isInstanceOf(PgGatewayUnavailableException.class)
                .hasCauseInstanceOf(CallNotPermittedException.class);
    }

    @Test
    @DisplayName("confirmFallback 은 BulkheadFullException 도 PgGatewayUnavailableException 으로 변환한다")
    void confirmFallback_bulkheadFull_translatesToGatewayUnavailable() {
        Bulkhead bulkhead = Bulkhead.ofDefaults("toss-payments");
        BulkheadFullException cause = BulkheadFullException.createBulkheadFullException(bulkhead);

        assertThatThrownBy(() -> adapter.confirmFallback("pk_test_123", "order-1", 30000L, cause))
                .isInstanceOf(PgGatewayUnavailableException.class)
                .hasCauseInstanceOf(BulkheadFullException.class);
    }

    @Test
    @DisplayName("confirmFallback 은 PgConfirmFailedException 을 defensive re-throw 한다 (R4j ignore-exceptions 가 보통 막지만 방어)")
    void confirmFallback_pgConfirmFailed_isReThrownUnchanged() {
        PgConfirmFailedException cause = new PgConfirmFailedException("4xx body");

        assertThatThrownBy(() -> adapter.confirmFallback("pk_test_123", "order-1", 30000L, cause))
                .isInstanceOf(PgConfirmFailedException.class)
                .isSameAs(cause);
    }

    @Test
    @DisplayName("cancelFallback 은 transport 실패를 PgGatewayUnavailableException 으로 변환한다")
    void cancelFallback_transport_translatesToGatewayUnavailable() {
        ConnectException cause = new ConnectException("connection refused");

        assertThatThrownBy(() -> adapter.cancelFallback("pk_test_123", "Order cancelled", cause))
                .isInstanceOf(PgGatewayUnavailableException.class)
                .hasCauseInstanceOf(ConnectException.class);
    }

    @Test
    @DisplayName("cancelFallback 은 PgConfirmFailedException 을 defensive re-throw 한다")
    void cancelFallback_pgConfirmFailed_isReThrownUnchanged() {
        PgConfirmFailedException cause = new PgConfirmFailedException("Cancel 4xx body");

        assertThatThrownBy(() -> adapter.cancelFallback("pk_test_123", "Order cancelled", cause))
                .isInstanceOf(PgConfirmFailedException.class)
                .isSameAs(cause);
    }
}
