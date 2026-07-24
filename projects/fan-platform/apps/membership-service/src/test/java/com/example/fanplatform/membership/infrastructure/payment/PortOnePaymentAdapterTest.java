package com.example.fanplatform.membership.infrastructure.payment;

import com.example.fanplatform.membership.domain.payment.PaymentGatewayPort.PaymentResult;
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
 * PortOne V2 verification adapter — fail-closed matrix against a stubbed PortOne
 * REST API (MockWebServer; no Spring context, no real keys → CI-safe, fast
 * {@code :check} unit).
 *
 * <p>Asserts the security-critical property: the adapter approves ONLY when the
 * PortOne record is {@code status=PAID} with a paid amount equal to what we charge
 * and currency KRW — a forged/failed/tampered/unknown payment is declined.
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

    private static MockResponse json(String body) {
        return new MockResponse().setHeader("Content-Type", "application/json").setBody(body);
    }

    @Test
    void approvesWhenPaidAndAmountMatchesAndKrw() throws Exception {
        server.enqueue(json("{\"status\":\"PAID\",\"currency\":\"KRW\",\"amount\":{\"total\":9900}}"));

        PaymentResult r = adapter().authorize(9900L, 1, "pay-abc", "idem-1");

        assertThat(r.approved()).isTrue();
        // The verified paymentId is the durable PG reference stored on the membership.
        assertThat(r.paymentRef()).isEqualTo("pay-abc");
        RecordedRequest req = server.takeRequest();
        assertThat(req.getPath()).isEqualTo("/payments/pay-abc");
        assertThat(req.getHeader("Authorization")).isEqualTo("PortOne test-secret");
    }

    @Test
    void declinesWhenStatusNotPaid() {
        server.enqueue(json("{\"status\":\"FAILED\",\"currency\":\"KRW\",\"amount\":{\"total\":9900}}"));
        assertThat(adapter().authorize(9900L, 1, "pay-abc", "idem-1").approved()).isFalse();
    }

    @Test
    void declinesWhenAmountMismatch_tamperGuard() {
        // Client obtained a paymentId for a cheaper charge → server-side amount check rejects it.
        server.enqueue(json("{\"status\":\"PAID\",\"currency\":\"KRW\",\"amount\":{\"total\":100}}"));
        assertThat(adapter().authorize(9900L, 1, "pay-abc", "idem-1").approved()).isFalse();
    }

    @Test
    void declinesWhenCurrencyNotKrw() {
        server.enqueue(json("{\"status\":\"PAID\",\"currency\":\"USD\",\"amount\":{\"total\":9900}}"));
        assertThat(adapter().authorize(9900L, 1, "pay-abc", "idem-1").approved()).isFalse();
    }

    @Test
    void declinesOnNotFound() {
        server.enqueue(new MockResponse().setResponseCode(404).setBody("{}"));
        assertThat(adapter().authorize(9900L, 1, "pay-unknown", "idem-1").approved()).isFalse();
    }

    @Test
    void declinesOnServerError() {
        server.enqueue(new MockResponse().setResponseCode(500));
        assertThat(adapter().authorize(9900L, 1, "pay-abc", "idem-1").approved()).isFalse();
    }

    @Test
    void declinesOnBlankPaymentIdWithoutCallingPortOne() {
        assertThat(adapter().authorize(9900L, 1, "   ", "idem-1").approved()).isFalse();
        assertThat(server.getRequestCount()).isZero();
    }
}
