package com.example.libs.payment.portone;

import com.example.libs.payment.PaymentAuthorization;
import com.example.libs.payment.PaymentVerificationRequest;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PortOne V2 verification adapter — fail-closed matrix against a stubbed PortOne REST API
 * (MockWebServer; no Spring context, no real keys → CI-safe, fast {@code :check} unit).
 *
 * <p>Asserts the security-critical property preserved 1:1 from the fan-platform original: the
 * adapter approves ONLY when the PortOne record is {@code status=PAID} with a paid amount equal
 * to what we charge and currency KRW — a forged/failed/tampered/unknown payment is declined,
 * and a PG error is fail-closed to declined (never thrown).
 */
class PortOnePaymentAdapterTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws IOException {
        server.shutdown();
    }

    private PortOnePaymentAdapter adapter() {
        return new PortOnePaymentAdapter(server.url("/").toString(), "test-secret", RestClient.builder());
    }

    private static PaymentVerificationRequest req(long amountMinor, String paymentReference) {
        return new PaymentVerificationRequest(paymentReference, amountMinor, "KRW", null);
    }

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    @Test
    void approvesWhenPaidAndAmountMatchesAndKrw() throws Exception {
        server.enqueue(json("{\"status\":\"PAID\",\"currency\":\"KRW\",\"amount\":{\"total\":9900}}"));

        PaymentAuthorization r = adapter().verify(req(9900L, "pay-abc"));

        assertThat(r.approved()).isTrue();
        // The verified paymentId is the durable PG reference; PortOne fills no method/receipt.
        assertThat(r.vendorPaymentRef()).isEqualTo("pay-abc");
        assertThat(r.paymentMethod()).isNull();
        assertThat(r.receiptUrl()).isNull();
        RecordedRequest req = server.takeRequest();
        assertThat(req.getPath()).isEqualTo("/payments/pay-abc");
        assertThat(req.getHeader("Authorization")).isEqualTo("PortOne test-secret");
    }

    @Test
    void declinesWhenStatusNotPaid() {
        server.enqueue(json("{\"status\":\"FAILED\",\"currency\":\"KRW\",\"amount\":{\"total\":9900}}"));
        assertThat(adapter().verify(req(9900L, "pay-abc")).approved()).isFalse();
    }

    @Test
    void declinesWhenAmountMismatch_tamperGuard() {
        // Client obtained a paymentId for a cheaper charge → server-side amount check rejects it.
        server.enqueue(json("{\"status\":\"PAID\",\"currency\":\"KRW\",\"amount\":{\"total\":100}}"));
        assertThat(adapter().verify(req(9900L, "pay-abc")).approved()).isFalse();
    }

    @Test
    void declinesWhenCurrencyNotKrw() {
        server.enqueue(json("{\"status\":\"PAID\",\"currency\":\"USD\",\"amount\":{\"total\":9900}}"));
        assertThat(adapter().verify(req(9900L, "pay-abc")).approved()).isFalse();
    }

    @Test
    void declinesOnNotFound() {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{}"));
        assertThat(adapter().verify(req(9900L, "pay-unknown")).approved()).isFalse();
    }

    @Test
    void declinesOnServerError() {
        server.enqueue(new MockResponse().setResponseCode(500));
        assertThat(adapter().verify(req(9900L, "pay-abc")).approved()).isFalse();
    }

    @Test
    void declinesOnBlankPaymentIdWithoutCallingPortOne() {
        assertThat(adapter().verify(req(9900L, "   ")).approved()).isFalse();
        assertThat(server.getRequestCount()).isZero();
    }
}
