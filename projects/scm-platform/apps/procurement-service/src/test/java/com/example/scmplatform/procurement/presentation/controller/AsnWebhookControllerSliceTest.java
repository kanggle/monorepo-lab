package com.example.scmplatform.procurement.presentation.controller;

import com.example.scmplatform.procurement.application.AsnView;
import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.domain.error.AsnOverreceiptException;
import com.example.scmplatform.procurement.domain.error.PoNotFoundException;
import com.example.scmplatform.procurement.infrastructure.security.WebhookSignatureVerifier;
import com.example.scmplatform.procurement.presentation.advice.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice tests for {@link AsnWebhookController}.
 *
 * <p>The ASN webhook is a public endpoint (shared-secret verified inside the
 * controller). Security filter chain is bypassed via
 * {@code @AutoConfigureMockMvc(addFilters = false)}.
 *
 * <p>Test count: 4
 */
@WebMvcTest(AsnWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, WebhookSignatureVerifier.class})
class AsnWebhookControllerSliceTest {

    private static final String URL = "/api/procurement/webhooks/asn";
    private static final String WEBHOOK_SECRET = "scm-supplier-webhook-secret";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    PurchaseOrderApplicationService service;

    // ---- helpers ----

    private AsnView asnView() {
        return new AsnView(
                "asn-001", "po-001", "scm", "ASN-REF-001",
                Instant.now(), null,
                List.of(new AsnView.LineView(
                        "asnline-001", "line-001",
                        new BigDecimal("5.00"), null))
        );
    }

    private String validRequestJson() throws Exception {
        return objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {{
            put("tenantId", "scm");
            put("poId", "po-001");
            put("supplierAsnRef", "ASN-REF-001");
            put("expectedArrivalAt", Instant.now().toString());
            put("lines", List.of(new java.util.HashMap<String, Object>() {{
                put("poLineId", "line-001");
                put("quantityShipped", "5.00");
            }}));
        }});
    }

    // ---- tests ----

    @Test
    @DisplayName("POST /webhooks/asn — 200 happy path with valid signature")
    void receiveAsnHappyPath() throws Exception {
        when(service.receiveAsn(any())).thenReturn(asnView());

        mockMvc.perform(post(URL)
                        .header("X-Supplier-Signature", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("asn-001"))
                .andExpect(jsonPath("$.data.supplierAsnRef").value("ASN-REF-001"));
    }

    @Test
    @DisplayName("POST /webhooks/asn — 401 when signature is missing")
    void receiveAsnMissingSignature() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /webhooks/asn — 401 when signature is wrong")
    void receiveAsnWrongSignature() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-Supplier-Signature", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /webhooks/asn — 422 ASN_OVERRECEIPT when shipped qty exceeds PO balance")
    void receiveAsnOverreceipt() throws Exception {
        when(service.receiveAsn(any()))
                .thenThrow(new AsnOverreceiptException("ASN qty exceeds remaining line balance"));

        mockMvc.perform(post(URL)
                        .header("X-Supplier-Signature", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ASN_OVERRECEIPT"));
    }
}
