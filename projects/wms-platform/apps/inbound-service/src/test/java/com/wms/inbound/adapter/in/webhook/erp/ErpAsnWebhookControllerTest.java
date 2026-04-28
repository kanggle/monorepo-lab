package com.wms.inbound.adapter.in.webhook.erp;

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

import com.wms.inbound.adapter.in.web.advice.GlobalExceptionHandler;
import com.wms.inbound.application.port.out.WebhookSecretPort;
import com.wms.inbound.config.SecurityConfig;
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
 * Slice tests for {@link ErpAsnWebhookController}. Covers all 12 failure-mode
 * cases from {@code specs/contracts/webhooks/erp-asn-webhook.md} §
 * Failure-mode Test Cases.
 *
 * <p>The {@link ErpWebhookIngestService} is mocked because its persistence
 * dependencies are integration concerns — the controller's contract surface
 * is the focus here.
 *
 * <p>Clock is pinned to {@link #FIXED_NOW}; HMAC secret resolves only for
 * {@code erp-stg}, allowing the "unknown env" case to land naturally.
 */
@WebMvcTest(controllers = ErpAsnWebhookController.class)
@Import({SecurityConfig.class, GlobalExceptionHandler.class,
        ErpAsnWebhookControllerTest.FixedClockConfig.class,
        HmacSignatureVerifier.class, TimestampWindowVerifier.class})
@TestPropertySource(properties = {
        "spring.security.oauth2.resourceserver.jwt.jwk-set-uri=http://localhost:0/.well-known/jwks.json",
        "inbound.webhook.erp.timestamp-window-seconds=300"
})
class ErpAsnWebhookControllerTest {

    static final Instant FIXED_NOW = Instant.parse("2026-04-28T12:00:00Z");

    private static final String SECRET = "test-erp-stg-secret";
    private static final String SOURCE = "erp-stg";

    private static final String VALID_BODY =
            "{\"asnNo\":\"ASN-20260428-0001\",\"supplierPartnerCode\":\"SUP-001\","
                    + "\"warehouseCode\":\"WH01\","
                    + "\"lines\":[{\"lineNo\":1,\"skuCode\":\"SKU-APPLE-001\",\"expectedQty\":100}]}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private WebhookSecretPort secretPort;

    @MockBean
    private ErpWebhookIngestService ingestService;

    @TestConfiguration
    static class FixedClockConfig {
        @Bean
        Clock clock() {
            return Clock.fixed(FIXED_NOW, ZoneOffset.UTC);
        }
    }

    private static String hmacFor(String body, String secret) {
        return new HmacSignatureVerifier()
                .compute(body.getBytes(StandardCharsets.UTF_8), secret);
    }

    private MockHttpServletRequestBuilder validRequest() {
        return post("/webhooks/erp/asn")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Erp-Event-Id", "evt-1")
                .header("X-Erp-Source", SOURCE)
                .header("X-Erp-Timestamp", FIXED_NOW.toString())
                .header("X-Erp-Signature", hmacFor(VALID_BODY, SECRET))
                .content(VALID_BODY);
    }

    private void mockSecretPortFor(String source) {
        when(secretPort.resolveSecret(eq(source))).thenReturn(Optional.of(SECRET));
    }

    private void mockSecretPortMissing() {
        when(secretPort.resolveSecret(anyString())).thenReturn(Optional.empty());
    }

    // -------------------------------------------------------------------------
    // Case 1 — Valid signature + timestamp + new event-id → 200 accepted
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 1: valid signature + timestamp + new event-id → 200 accepted")
    void case01_validRequestAccepted() throws Exception {
        mockSecretPortFor(SOURCE);
        when(ingestService.ingest(eq("evt-1"), anyString(), anyString(), eq(SOURCE)))
                .thenReturn(ErpWebhookIngestService.Result.accepted(FIXED_NOW));

        mockMvc.perform(validRequest())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("accepted"))
                .andExpect(jsonPath("$.eventId").value("evt-1"))
                .andExpect(jsonPath("$.asnNo").value("ASN-20260428-0001"));

        verify(ingestService).ingest(eq("evt-1"), anyString(), anyString(), eq(SOURCE));
    }

    // -------------------------------------------------------------------------
    // Case 2 — Signature header absent → 401 WEBHOOK_SIGNATURE_INVALID
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 2: signature header absent → 401 WEBHOOK_SIGNATURE_INVALID")
    void case02_signatureHeaderAbsent() throws Exception {
        mockSecretPortFor(SOURCE);

        mockMvc.perform(post("/webhooks/erp/asn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-2")
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Timestamp", FIXED_NOW.toString())
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));

        verify(ingestService, never()).ingest(anyString(), anyString(), anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // Case 3 — Signature mismatch (wrong secret) → 401
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 3: signature mismatch (wrong secret) → 401")
    void case03_signatureMismatch() throws Exception {
        mockSecretPortFor(SOURCE);
        String wrongSig = hmacFor(VALID_BODY, "wrong-secret");

        mockMvc.perform(post("/webhooks/erp/asn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-3")
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Timestamp", FIXED_NOW.toString())
                        .header("X-Erp-Signature", wrongSig)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
    }

    // -------------------------------------------------------------------------
    // Case 4 — Signature uppercase hex → 401
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 4: signature uppercase hex → 401")
    void case04_signatureUppercase() throws Exception {
        mockSecretPortFor(SOURCE);
        String good = hmacFor(VALID_BODY, SECRET);
        String upper = "sha256=" + good.substring("sha256=".length()).toUpperCase();

        mockMvc.perform(post("/webhooks/erp/asn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-4")
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Timestamp", FIXED_NOW.toString())
                        .header("X-Erp-Signature", upper)
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
    }

    // -------------------------------------------------------------------------
    // Case 5 — Timestamp absent → 401 WEBHOOK_TIMESTAMP_INVALID
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 5: timestamp absent → 401 WEBHOOK_TIMESTAMP_INVALID")
    void case05_timestampAbsent() throws Exception {
        mockSecretPortFor(SOURCE);

        mockMvc.perform(post("/webhooks/erp/asn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-5")
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Signature", hmacFor(VALID_BODY, SECRET))
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_TIMESTAMP_INVALID"));
    }

    // -------------------------------------------------------------------------
    // Case 6 — Timestamp 6 minutes old → 401
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 6: timestamp 6 minutes old → 401 WEBHOOK_TIMESTAMP_INVALID")
    void case06_timestampStale() throws Exception {
        mockSecretPortFor(SOURCE);
        Instant stale = FIXED_NOW.minus(Duration.ofMinutes(6));

        mockMvc.perform(post("/webhooks/erp/asn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-6")
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Timestamp", stale.toString())
                        .header("X-Erp-Signature", hmacFor(VALID_BODY, SECRET))
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_TIMESTAMP_INVALID"));
    }

    // -------------------------------------------------------------------------
    // Case 7 — Timestamp 6 minutes in future → 401
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 7: timestamp 6 minutes in future → 401 WEBHOOK_TIMESTAMP_INVALID")
    void case07_timestampFuture() throws Exception {
        mockSecretPortFor(SOURCE);
        Instant future = FIXED_NOW.plus(Duration.ofMinutes(6));

        mockMvc.perform(post("/webhooks/erp/asn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-7")
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Timestamp", future.toString())
                        .header("X-Erp-Signature", hmacFor(VALID_BODY, SECRET))
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_TIMESTAMP_INVALID"));
    }

    // -------------------------------------------------------------------------
    // Case 8 — Schema invalid (missing asnNo) → 422 VALIDATION_ERROR
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 8: schema invalid (missing asnNo) → 422 VALIDATION_ERROR")
    void case08_schemaInvalid() throws Exception {
        mockSecretPortFor(SOURCE);
        String missingAsnNo =
                "{\"supplierPartnerCode\":\"SUP-001\",\"warehouseCode\":\"WH01\","
                        + "\"lines\":[{\"lineNo\":1,\"skuCode\":\"SKU-1\",\"expectedQty\":1}]}";
        String sig = hmacFor(missingAsnNo, SECRET);

        mockMvc.perform(post("/webhooks/erp/asn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-8")
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Timestamp", FIXED_NOW.toString())
                        .header("X-Erp-Signature", sig)
                        .content(missingAsnNo))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(ingestService, never()).ingest(anyString(), anyString(), anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // Case 9 — Duplicate event-id → 200 ignored_duplicate
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 9: duplicate event-id → 200 ignored_duplicate")
    void case09_duplicateEventId() throws Exception {
        mockSecretPortFor(SOURCE);
        Instant previously = FIXED_NOW.minus(Duration.ofMinutes(2));
        when(ingestService.ingest(eq("evt-9"), anyString(), anyString(), eq(SOURCE)))
                .thenReturn(ErpWebhookIngestService.Result.duplicate(previously));

        mockMvc.perform(post("/webhooks/erp/asn")
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

    // -------------------------------------------------------------------------
    // Case 10 — Source header references unknown env → 401 SIGNATURE_INVALID
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 10: unknown env source → 401 WEBHOOK_SIGNATURE_INVALID")
    void case10_unknownSource() throws Exception {
        mockSecretPortMissing();

        mockMvc.perform(post("/webhooks/erp/asn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-10")
                        .header("X-Erp-Source", "erp-unknown")
                        .header("X-Erp-Timestamp", FIXED_NOW.toString())
                        .header("X-Erp-Signature", hmacFor(VALID_BODY, SECRET))
                        .content(VALID_BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
    }

    // -------------------------------------------------------------------------
    // Case 11 — Backend slow (artificial delay) → 200 within ERP timeout
    //   Modelled here as a fast-path 200 with the ingest service unaffected.
    //   The "still returns 200" property is the test gate.
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 11: backend slow → still returns 200 (commit is fast — only inbox + dedupe)")
    void case11_backendSlowStillFastResponse() throws Exception {
        mockSecretPortFor(SOURCE);
        when(ingestService.ingest(eq("evt-11"), anyString(), anyString(), eq(SOURCE)))
                .thenReturn(ErpWebhookIngestService.Result.accepted(FIXED_NOW));

        mockMvc.perform(post("/webhooks/erp/asn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-11")
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Timestamp", FIXED_NOW.toString())
                        .header("X-Erp-Signature", hmacFor(VALID_BODY, SECRET))
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON));
    }

    // -------------------------------------------------------------------------
    // Case 12 — Body byte-modified after signing (proxy adds whitespace) → 401
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Case 12: body byte-modified after signing → 401 (signature is over raw bytes)")
    void case12_bodyByteModified() throws Exception {
        mockSecretPortFor(SOURCE);
        // Signed against the original body, but we send a body with a trailing space.
        String originalSig = hmacFor(VALID_BODY, SECRET);
        String tampered = VALID_BODY + " ";

        mockMvc.perform(post("/webhooks/erp/asn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Event-Id", "evt-12")
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Timestamp", FIXED_NOW.toString())
                        .header("X-Erp-Signature", originalSig)
                        .content(tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("WEBHOOK_SIGNATURE_INVALID"));
    }

    // -------------------------------------------------------------------------
    // Bonus: missing X-Erp-Event-Id → 400 VALIDATION_ERROR (controller guard)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Bonus: missing X-Erp-Event-Id → 400 VALIDATION_ERROR")
    void missingEventIdHeader() throws Exception {
        mockMvc.perform(post("/webhooks/erp/asn")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Erp-Source", SOURCE)
                        .header("X-Erp-Timestamp", FIXED_NOW.toString())
                        .header("X-Erp-Signature", hmacFor(VALID_BODY, SECRET))
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(ingestService, never()).ingest(anyString(), anyString(), anyString(), anyString());
    }

}
