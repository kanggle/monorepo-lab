package com.example.libs.payment.toss;

import com.example.libs.payment.PaymentAuthorization;
import com.example.libs.payment.PaymentGatewayStatus;
import com.example.libs.payment.PaymentVerificationRequest;
import com.example.libs.payment.PgConfirmFailedException;
import com.example.libs.payment.PgGatewayUnavailableException;
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

@DisplayName("TossPaymentsAdapter WireMock 테스트 (libs:payment-toss)")
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

    private static PaymentVerificationRequest req(String paymentKey, String orderId, long amount) {
        return new PaymentVerificationRequest(paymentKey, amount, "KRW", orderId);
    }

    // --- verify (= confirm / money-capture) ---

    @Test
    @DisplayName("PG 승인(capture) 성공 시 approved + paymentMethod + receiptUrl 을 반환한다")
    void verify_success_returnsResult() {
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

        PaymentAuthorization result = adapter.verify(req("pk_test_123", "order-1", 30000L));

        assertThat(result.approved()).isTrue();
        assertThat(result.vendorPaymentRef()).isEqualTo("pk_test_123");
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
    void verify_noReceipt_returnsNullReceiptUrl() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments/confirm"))
                .willReturn(okJson("""
                        {
                            "paymentKey": "pk_test_123",
                            "orderId": "order-1",
                            "status": "DONE",
                            "method": "TRANSFER"
                        }
                        """)));

        PaymentAuthorization result = adapter.verify(req("pk_test_123", "order-1", 30000L));

        assertThat(result.paymentMethod()).isEqualTo("TRANSFER");
        assertThat(result.receiptUrl()).isNull();
    }

    @Test
    @DisplayName("PG 승인 4xx 에러 시 PgConfirmFailedException이 발생한다")
    void verify_clientError_throwsPgConfirmFailed() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments/confirm"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":"ALREADY_PROCESSED_PAYMENT","message":"이미 처리된 결제입니다."}
                                """)));

        assertThatThrownBy(() -> adapter.verify(req("pk_test_123", "order-1", 30000L)))
                .isInstanceOf(PgConfirmFailedException.class);
    }

    @Test
    @DisplayName("PG 승인 5xx 에러는 adapter 에서 catch 하지 않고 HttpServerErrorException 으로 전파된다 (ADR-MONO-005 § D4 — R4j retry-exceptions 가 받는다)")
    void verify_serverError_propagatesHttpServerErrorException() {
        wireMock.stubFor(post(urlEqualTo("/v1/payments/confirm"))
                .willReturn(serverError()
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":"INTERNAL_ERROR","message":"서버 내부 오류"}
                                """)));

        assertThatThrownBy(() -> adapter.verify(req("pk_test_123", "order-1", 30000L)))
                .isInstanceOf(HttpServerErrorException.class);
    }

    // --- refund (full cancel) ---

    @Test
    @DisplayName("PG 취소 성공 시 예외 없이 완료된다")
    void refund_success_completesWithoutException() {
        wireMock.stubFor(post(urlPathEqualTo("/v1/payments/pk_test_123/cancel"))
                .willReturn(okJson("""
                        {"paymentKey":"pk_test_123","status":"CANCELED"}
                        """)));

        adapter.refund("pk_test_123", "Order cancelled");

        wireMock.verify(postRequestedFor(urlPathEqualTo("/v1/payments/pk_test_123/cancel"))
                .withRequestBody(matchingJsonPath("$.cancelReason", equalTo("Order cancelled"))));
    }

    @Test
    @DisplayName("PG 취소 4xx 실패 시 PgConfirmFailedException이 발생한다")
    void refund_clientError_throwsPgConfirmFailed() {
        wireMock.stubFor(post(urlPathEqualTo("/v1/payments/pk_test_123/cancel"))
                .willReturn(aResponse()
                        .withStatus(400)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":"ALREADY_CANCELED_PAYMENT","message":"이미 취소된 결제입니다."}
                                """)));

        assertThatThrownBy(() -> adapter.refund("pk_test_123", "Order cancelled"))
                .isInstanceOf(PgConfirmFailedException.class);
    }

    @Test
    @DisplayName("PG 취소 5xx 는 adapter 에서 catch 하지 않고 HttpServerErrorException 으로 전파된다")
    void refund_serverError_propagatesHttpServerErrorException() {
        wireMock.stubFor(post(urlPathEqualTo("/v1/payments/pk_test_123/cancel"))
                .willReturn(serverError()));

        assertThatThrownBy(() -> adapter.refund("pk_test_123", "Order cancelled"))
                .isInstanceOf(HttpServerErrorException.class);
    }

    // --- fetchStatus (TASK-BE-438 double-refund guard) ---

    @Test
    @DisplayName("fetchStatus 가 Toss status=CANCELED 면 PaymentGatewayStatus.CANCELED 로 매핑한다")
    void fetchStatus_canceled_mapsToCanceled() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/payments/pk_test_123"))
                .willReturn(okJson("""
                        {"paymentKey":"pk_test_123","status":"CANCELED"}
                        """)));

        assertThat(adapter.fetchStatus("pk_test_123")).isEqualTo(PaymentGatewayStatus.CANCELED);
    }

    @Test
    @DisplayName("fetchStatus 가 Toss status=DONE 면 PaymentGatewayStatus.CAPTURED 로 매핑한다")
    void fetchStatus_done_mapsToCaptured() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/payments/pk_test_123"))
                .willReturn(okJson("""
                        {"paymentKey":"pk_test_123","status":"DONE"}
                        """)));

        assertThat(adapter.fetchStatus("pk_test_123")).isEqualTo(PaymentGatewayStatus.CAPTURED);
    }

    @Test
    @DisplayName("fetchStatus 가 알 수 없는/누락된 status 면 PaymentGatewayStatus.UNKNOWN 으로 매핑한다 (RESOLVED 로 추정 금지)")
    void fetchStatus_unknownStatus_mapsToUnknown() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/payments/pk_test_123"))
                .willReturn(okJson("""
                        {"paymentKey":"pk_test_123","status":"ABORTED"}
                        """)));

        assertThat(adapter.fetchStatus("pk_test_123")).isEqualTo(PaymentGatewayStatus.UNKNOWN);
    }

    @Test
    @DisplayName("fetchStatus 4xx 는 PgConfirmFailedException 으로 변환된다 (read 오류를 RESOLVED 로 추정하지 않음)")
    void fetchStatus_clientError_throwsPgConfirmFailed() {
        wireMock.stubFor(get(urlPathEqualTo("/v1/payments/pk_test_123"))
                .willReturn(aResponse()
                        .withStatus(404)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"code":"NOT_FOUND_PAYMENT","message":"존재하지 않는 결제입니다."}
                                """)));

        assertThatThrownBy(() -> adapter.fetchStatus("pk_test_123"))
                .isInstanceOf(PgConfirmFailedException.class);
    }

    @Test
    @DisplayName("fetchStatusFallback 은 transport 실패를 PgGatewayUnavailableException 으로 변환한다")
    void fetchStatusFallback_transport_translatesToGatewayUnavailable() {
        ConnectException cause = new ConnectException("connection refused");

        assertThatThrownBy(() -> adapter.fetchStatusFallback("pk_test_123", cause))
                .isInstanceOf(PgGatewayUnavailableException.class)
                .hasCauseInstanceOf(ConnectException.class);
    }

    // --- Fallback method classification (ADR-MONO-005 § D4) ---

    @Test
    @DisplayName("verifyFallback 은 HttpServerErrorException cause 를 PgGatewayUnavailableException 으로 변환한다")
    void verifyFallback_serverError_translatesToGatewayUnavailable() {
        HttpServerErrorException cause = HttpServerErrorException.create(
                org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR,
                "Internal Server Error", null, new byte[0], null);

        assertThatThrownBy(() -> adapter.verifyFallback(req("pk_test_123", "order-1", 30000L), cause))
                .isInstanceOf(PgGatewayUnavailableException.class)
                .hasMessageContaining("pk_test_123")
                .hasMessageContaining("exhausted")
                .hasCauseInstanceOf(HttpServerErrorException.class);
    }

    @Test
    @DisplayName("verifyFallback 은 CallNotPermittedException (CB OPEN) 도 PgGatewayUnavailableException 으로 변환한다")
    void verifyFallback_circuitOpen_translatesToGatewayUnavailable() {
        CircuitBreaker cb = CircuitBreaker.of("toss-payments", CircuitBreakerConfig.ofDefaults());
        CallNotPermittedException cause = CallNotPermittedException.createCallNotPermittedException(cb);

        assertThatThrownBy(() -> adapter.verifyFallback(req("pk_test_123", "order-1", 30000L), cause))
                .isInstanceOf(PgGatewayUnavailableException.class)
                .hasCauseInstanceOf(CallNotPermittedException.class);
    }

    @Test
    @DisplayName("verifyFallback 은 BulkheadFullException 도 PgGatewayUnavailableException 으로 변환한다")
    void verifyFallback_bulkheadFull_translatesToGatewayUnavailable() {
        Bulkhead bulkhead = Bulkhead.ofDefaults("toss-payments");
        BulkheadFullException cause = BulkheadFullException.createBulkheadFullException(bulkhead);

        assertThatThrownBy(() -> adapter.verifyFallback(req("pk_test_123", "order-1", 30000L), cause))
                .isInstanceOf(PgGatewayUnavailableException.class)
                .hasCauseInstanceOf(BulkheadFullException.class);
    }

    @Test
    @DisplayName("verifyFallback 은 PgConfirmFailedException 을 defensive re-throw 한다 (R4j ignore-exceptions 가 보통 막지만 방어)")
    void verifyFallback_pgConfirmFailed_isReThrownUnchanged() {
        PgConfirmFailedException cause = new PgConfirmFailedException("4xx body");

        assertThatThrownBy(() -> adapter.verifyFallback(req("pk_test_123", "order-1", 30000L), cause))
                .isInstanceOf(PgConfirmFailedException.class)
                .isSameAs(cause);
    }

    @Test
    @DisplayName("refundFallback 은 transport 실패를 PgGatewayUnavailableException 으로 변환한다")
    void refundFallback_transport_translatesToGatewayUnavailable() {
        ConnectException cause = new ConnectException("connection refused");

        assertThatThrownBy(() -> adapter.refundFallback("pk_test_123", "Order cancelled", cause))
                .isInstanceOf(PgGatewayUnavailableException.class)
                .hasCauseInstanceOf(ConnectException.class);
    }

    @Test
    @DisplayName("refundFallback 은 PgConfirmFailedException 을 defensive re-throw 한다")
    void refundFallback_pgConfirmFailed_isReThrownUnchanged() {
        PgConfirmFailedException cause = new PgConfirmFailedException("Cancel 4xx body");

        assertThatThrownBy(() -> adapter.refundFallback("pk_test_123", "Order cancelled", cause))
                .isInstanceOf(PgConfirmFailedException.class)
                .isSameAs(cause);
    }
}
