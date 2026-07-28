package com.example.libs.payment.portone;

import com.example.libs.payment.PaymentAuthorization;
import com.example.libs.payment.PgGatewayUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * PortOne V2 billing-key charge (recurring) — money-WRITE failure matrix against a stubbed PortOne
 * REST API (MockWebServer; no Spring context, no real keys → CI-safe unit).
 *
 * <p>Asserts the money-safety contract of {@code chargeBillingKey} (ADR-MONO-057): a PAID charge
 * for the matching amount is approved; a <b>definitive</b> PG rejection (4xx / a FAILED body /
 * blank key) is declined; an <b>ambiguous</b> outcome (5xx, empty body, non-terminal status, or a
 * PAID-but-mismatched amount) is surfaced as a thrown {@link PgGatewayUnavailableException} — never
 * silently declined, because the charge may have actually captured and a blind retry would
 * double-charge.
 *
 * <p>Separate from {@code PortOnePaymentAdapterTest} by design: that class covers the already
 * live-verified {@code verify} read and must stay untouched (its assertions are the regression
 * baseline for "don't change verify").
 */
class PortOneRecurringBillingTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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
    void approvesAndSendsExpectedRequestWhenPaidAndAmountMatches() throws Exception {
        server.enqueue(json("{\"status\":\"PAID\",\"currency\":\"KRW\",\"amount\":{\"total\":9900}}"));

        PaymentAuthorization r = adapter().chargeBillingKey("bk-123", "pay-abc", 9900L, "KRW", "월 정기결제");

        assertThat(r.approved()).isTrue();
        assertThat(r.vendorPaymentRef()).isEqualTo("pay-abc");
        assertThat(r.paymentMethod()).isNull();
        assertThat(r.receiptUrl()).isNull();

        RecordedRequest req = server.takeRequest();
        assertThat(req.getMethod()).isEqualTo("POST");
        assertThat(req.getPath()).isEqualTo("/payments/pay-abc/billing-key");
        assertThat(req.getHeader("Authorization")).isEqualTo("PortOne test-secret");
        JsonNode sent = MAPPER.readTree(req.getBody().readUtf8());
        assertThat(sent.get("billingKey").asText()).isEqualTo("bk-123");
        assertThat(sent.get("orderName").asText()).isEqualTo("월 정기결제");
        assertThat(sent.get("currency").asText()).isEqualTo("KRW");
        assertThat(sent.get("amount").get("total").asLong()).isEqualTo(9900L);
    }

    @Test
    void declinesOnBlankBillingKeyWithoutCallingPortOne() {
        assertThat(adapter().chargeBillingKey("   ", "pay-abc", 9900L, "KRW", "n").approved()).isFalse();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void declinesOnBlankPaymentIdWithoutCallingPortOne() {
        assertThat(adapter().chargeBillingKey("bk-123", "  ", 9900L, "KRW", "n").approved()).isFalse();
        assertThat(server.getRequestCount()).isZero();
    }

    @Test
    void declinesOnDefinitive4xxRejection() {
        // Invalid/revoked billing key or insufficient funds → PortOne 4xx. Unambiguous no-money.
        server.enqueue(new MockResponse().setResponseCode(400).setBody("{\"type\":\"INVALID_BILLING_KEY\"}"));
        assertThat(adapter().chargeBillingKey("bk-x", "pay-abc", 9900L, "KRW", "n").approved()).isFalse();
    }

    @Test
    void declinesWhenBodyStatusFailed() {
        // Terminal FAILED delivered in a 2xx body → unambiguous no charge stands.
        server.enqueue(json("{\"status\":\"FAILED\",\"currency\":\"KRW\",\"amount\":{\"total\":9900}}"));
        assertThat(adapter().chargeBillingKey("bk-123", "pay-abc", 9900L, "KRW", "n").approved()).isFalse();
    }

    @Test
    void throwsUnavailableOnServerError_ambiguous() {
        // 5xx — PG-side outcome unknown. Money MAY have moved. Must NOT decline (would invite a
        // double-charging retry); surface the ambiguity for verify()-based reconciliation.
        server.enqueue(new MockResponse().setResponseCode(500));
        assertThatThrownBy(() -> adapter().chargeBillingKey("bk-123", "pay-abc", 9900L, "KRW", "n"))
                .isInstanceOf(PgGatewayUnavailableException.class);
    }

    @Test
    void throwsUnavailableOnEmptyBody_ambiguous() {
        server.enqueue(new MockResponse().setResponseCode(200));
        assertThatThrownBy(() -> adapter().chargeBillingKey("bk-123", "pay-abc", 9900L, "KRW", "n"))
                .isInstanceOf(PgGatewayUnavailableException.class);
    }

    @Test
    void throwsUnavailableOnNonTerminalStatus_ambiguous() {
        // READY / PAY_PENDING — not captured, not definitively failed. Reconcile, don't guess.
        server.enqueue(json("{\"status\":\"PAY_PENDING\",\"currency\":\"KRW\",\"amount\":{\"total\":9900}}"));
        assertThatThrownBy(() -> adapter().chargeBillingKey("bk-123", "pay-abc", 9900L, "KRW", "n"))
                .isInstanceOf(PgGatewayUnavailableException.class);
    }

    @Test
    void throwsUnavailableWhenPaidButAmountMismatch_moneyAnomaly() {
        // Status PAID but the captured amount differs from what we charged → a money anomaly we
        // must neither approve cleanly nor mark declined (money moved). Reconcile.
        server.enqueue(json("{\"status\":\"PAID\",\"currency\":\"KRW\",\"amount\":{\"total\":100}}"));
        assertThatThrownBy(() -> adapter().chargeBillingKey("bk-123", "pay-abc", 9900L, "KRW", "n"))
                .isInstanceOf(PgGatewayUnavailableException.class);
    }
}
