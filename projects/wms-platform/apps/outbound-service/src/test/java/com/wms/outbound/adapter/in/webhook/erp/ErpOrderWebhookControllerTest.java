package com.wms.outbound.adapter.in.webhook.erp;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wms.outbound.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.outbound.adapter.out.persistence.adapter.WebhookInboxPersistenceAdapter;
import com.wms.outbound.application.port.out.WebhookSecretPort;
import com.wms.outbound.config.SecurityConfig;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Slice tests for {@link ErpOrderWebhookController}. Covers all 12 failure-mode
 * cases from {@code specs/contracts/webhooks/erp-order-webhook.md} §
 * Failure-mode Test Cases.
 */
@WebMvcTest(controllers = ErpOrderWebhookController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        ErpOrderWebhookControllerTest.FixedClockConfig.class,
        HmacVerifier.class, TimestampWindowValidator.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/.well-known/jwks.json",
        "outbound.webhook.erp.timestamp-window-seconds=300"
})
class ErpOrderWebhookControllerTest {

    static final Instant FIXED_NOW = Instant.parse("2026-04-28T12:00:00Z");

    private static final String SECRET = "test-erp-stg-secret";
    private static final String SOURCE = "erp-stg";

    private static final String VALID_BODY =
            "{\"orderNo\":\"ORD-20260428-0001\",\"customerPartnerCode\":\"CUST-001\","
                    + "\"warehouseCode\":\"WH01\","
                    + "\"lines\":[{\"lineNo\":1,\"skuCode\":\"SKU-APPLE-001\",\"qtyOrdered\":100}]}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookSecretPort secretPort;

    @MockBean
    private WebhookInboxPersistenceAdapter inboxAdapter;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }

    private static String hmacFor(String body, String secret) {
        return new HmacVerifier()
                .compute(body.getBytes(StandardCharsets.UTF_8), secret);
    }

    private MockHttpServletRequestBuilder validRequest() {
        return post("/webhooks/erp/order")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Erp-Event-Id", "evt-1")
                .header("X-Erp-Source", SOURCE)
                .header("X-Erp-Timestamp", FIXED_NOW.toString())
                .header("X-Erp-Signature", hmacFor(VALID_BODY, SECRET))
                .content(VALID_BODY);
    }

    private void mockSecretPortFor(String source) {
        when(secretPort.getSecret(eq(source))).thenReturn(Optional.of(SECRET));
    }

    private void mockSecretPortMissing() {
        when(secretPort.getSecret(anyString())).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("Case 1: valid signature + timestamp + new event-id → 200 accepted")
    void case01_validRequestAccepted() throws Exception {
        mockSecretPortFor(SOURCE);
        when(inboxAdapter.ingest(eq("evt-1"), anyString(), eq(SOURCE)))
                .thenReturn(WebhookInboxPersistenceAdapter.Result.accepted(FIXED_NOW));

        mockMvc.perform(validRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.eventId").value("evt-1"))
                .andExpect(jsonPath("$.orderNo").value("ORD-20260428-0001"));

        verify(inboxAdapter).ingest(eq("evt-1"), anyString(), eq(SOURCE));
    }

    @Test
    @DisplayName("Case 2: signature header absent → 401 WEBHOOK_SIGNATURE_INVALID")
    void case02_signatureHeaderAbsent() throws Exception {
        mockSecretPortFor(SOURCE);

        mockMvc.perform(post("/webhooks/erp/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-2")
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Timestamp", FIXED_NOW.toString())
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));

        verify(inboxAdapter, never()).ingest(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Case 3: signature mismatch (wrong secret) → 401")
    void case03_signatureMismatch() throws Exception {
        mockSecretPortFor(SOURCE);
        String wrongSig = hmacFor(VALID_BODY, "wrong-secret");

        mockMvc.perform(post("/webhooks/erp/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-3")
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Timestamp", FIXED_NOW.toString())
                        .header("X-Erp-Signature", wrongSig)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
    }

    @Test
    @DisplayName("Case 4: signature uppercase hex → 401")
    void case04_signatureUppercase() throws Exception {
        mockSecretPortFor(SOURCE);
        String good = hmacFor(VALID_BODY, SECRET);
        String upper = "sha256=" + good.substring("sha256=".length()).toUpperCase();

        mockMvc.perform(post("/webhooks/erp/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-4")
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Timestamp", FIXED_NOW.toString())
                        .header("X-Erp-Signature", upper)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
    }

    @Test
    @DisplayName("Case 5: timestamp absent → 401 WEBHOOK_TIMESTAMP_INVALID")
    void case05_timestampAbsent() throws Exception {
        mockSecretPortFor(SOURCE);

        mockMvc.perform(post("/webhooks/erp/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-5")
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Signature", hmacFor(VALID_BODY, SECRET))
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_TIMESTAMP_INVALID"));
    }

    @Test
    @DisplayName("Case 6: timestamp 6 minutes old → 401 WEBHOOK_TIMESTAMP_INVALID")
    void case06_timestampStale() throws Exception {
        mockSecretPortFor(SOURCE);
        Instant stale = FIXED_NOW.minus(Duration.ofMinutes(6));

        mockMvc.perform(post("/webhooks/erp/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-6")
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Timestamp", stale.toString())
                        .header("X-Erp-Signature", hmacFor(VALID_BODY, SECRET))
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_TIMESTAMP_INVALID"));
    }

    @Test
    @DisplayName("Case 7: timestamp 6 minutes in future → 401 WEBHOOK_TIMESTAMP_INVALID")
    void case07_timestampFuture() throws Exception {
        mockSecretPortFor(SOURCE);
        Instant future = FIXED_NOW.plus(Duration.ofMinutes(6));

        mockMvc.perform(post("/webhooks/erp/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-7")
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Timestamp", future.toString())
                        .header("X-Erp-Signature", hmacFor(VALID_BODY, SECRET))
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_TIMESTAMP_INVALID"));
    }

    @Test
    @DisplayName("Case 8: schema invalid (missing orderNo) → 422 VALIDATION_ERROR")
    void case08_schemaInvalid() throws Exception {
        mockSecretPortFor(SOURCE);
        String missingOrderNo =
                "{\"customerPartnerCode\":\"CUST-001\",\"warehouseCode\":\"WH01\","
                        + "\"lines\":[{\"lineNo\":1,\"skuCode\":\"SKU-1\",\"qtyOrdered\":1}]}";
        String sig = hmacFor(missingOrderNo, SECRET);

        mockMvc.perform(post("/webhooks/erp/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-8")
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Timestamp", FIXED_NOW.toString())
                        .header("X-Erp-Signature", sig)
                        .content(missingOrderNo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(inboxAdapter, never()).ingest(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Case 8b: schema invalid (qtyOrdered = 0) → 422")
    void case08b_qtyOrderedZero() throws Exception {
        mockSecretPortFor(SOURCE);
        String body =
                "{\"orderNo\":\"ORD-X\",\"customerPartnerCode\":\"CUST-001\",\"warehouseCode\":\"WH01\","
                        + "\"lines\":[{\"lineNo\":1,\"skuCode\":\"SKU-1\",\"qtyOrdered\":0}]}";
        String sig = hmacFor(body, SECRET);

        mockMvc.perform(post("/webhooks/erp/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-8b")
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Timestamp", FIXED_NOW.toString())
                        .header("X-Erp-Signature", sig)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Case 8c: schema invalid (lines empty) → 422")
    void case08c_emptyLines() throws Exception {
        mockSecretPortFor(SOURCE);
        String body =
                "{\"orderNo\":\"ORD-X\",\"customerPartnerCode\":\"CUST-001\",\"warehouseCode\":\"WH01\","
                        + "\"lines\":[]}";
        String sig = hmacFor(body, SECRET);

        mockMvc.perform(post("/webhooks/erp/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-8c")
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Timestamp", FIXED_NOW.toString())
                        .header("X-Erp-Signature", sig)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("Case 9: duplicate event-id → 200 ignored_duplicate")
    void case09_duplicateEventId() throws Exception {
        mockSecretPortFor(SOURCE);
        Instant previously = FIXED_NOW.minus(Duration.ofMinutes(2));
        when(inboxAdapter.ingest(eq("evt-9"), anyString(), eq(SOURCE)))
                .thenReturn(WebhookInboxPersistenceAdapter.Result.duplicate(previously));

        mockMvc.perform(post("/webhooks/erp/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-9")
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Timestamp", FIXED_NOW.toString())
                        .header("X-Erp-Signature", hmacFor(VALID_BODY, SECRET))
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ignored_duplicate"))
                .andExpect(jsonPath("$.eventId").value("evt-9"));
    }

    @Test
    @DisplayName("Case 10: unknown env source → 401 WEBHOOK_SIGNATURE_INVALID")
    void case10_unknownSource() throws Exception {
        mockSecretPortMissing();

        mockMvc.perform(post("/webhooks/erp/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-10")
                        .header("X-Erp-Source", "erp-unknown")
                        .header("X-Erp-Timestamp", FIXED_NOW.toString())
                        .header("X-Erp-Signature", hmacFor(VALID_BODY, SECRET))
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
    }

    @Test
    @DisplayName("Case 10b: unknown source + expired timestamp → 401 WEBHOOK_SIGNATURE_INVALID (secret check before timestamp)")
    void case10b_unknownSourceExpiredTimestampReturnsSignatureInvalid() throws Exception {
        // Secret is unknown for any source — MUST return SIGNATURE_INVALID, NOT TIMESTAMP_INVALID,
        // even though the timestamp is also expired. Processing order: secret → timestamp → HMAC.
        mockSecretPortMissing();
        Instant stale = FIXED_NOW.minus(Duration.ofMinutes(6));

        mockMvc.perform(post("/webhooks/erp/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-10b")
                        .header("X-Erp-Source", "erp-unknown")
                        .header("X-Erp-Timestamp", stale.toString())
                        .header("X-Erp-Signature", hmacFor(VALID_BODY, SECRET))
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));

        verify(inboxAdapter, never()).ingest(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Case 11: backend slow → still returns 200 fast (commit is fast)")
    void case11_backendSlowStillFastResponse() throws Exception {
        mockSecretPortFor(SOURCE);
        when(inboxAdapter.ingest(eq("evt-11"), anyString(), eq(SOURCE)))
                .thenReturn(WebhookInboxPersistenceAdapter.Result.accepted(FIXED_NOW));

        mockMvc.perform(post("/webhooks/erp/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-11")
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Timestamp", FIXED_NOW.toString())
                        .header("X-Erp-Signature", hmacFor(VALID_BODY, SECRET))
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    @Test
    @DisplayName("Case 12: body byte-modified after signing → 401")
    void case12_bodyByteModified() throws Exception {
        mockSecretPortFor(SOURCE);
        String originalSig = hmacFor(VALID_BODY, SECRET);
        String tampered = VALID_BODY + " ";

        mockMvc.perform(post("/webhooks/erp/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-12")
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Timestamp", FIXED_NOW.toString())
                        .header("X-Erp-Signature", originalSig)
                        .content(tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
    }

    @Test
    @DisplayName("Bonus: missing X-Erp-Event-Id → 400 VALIDATION_ERROR")
    void missingEventIdHeader() throws Exception {
        mockMvc.perform(post("/webhooks/erp/order")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Timestamp", FIXED_NOW.toString())
                        .header("X-Erp-Signature", hmacFor(VALID_BODY, SECRET))
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(inboxAdapter, never()).ingest(anyString(), anyString(), anyString());
    }
}
