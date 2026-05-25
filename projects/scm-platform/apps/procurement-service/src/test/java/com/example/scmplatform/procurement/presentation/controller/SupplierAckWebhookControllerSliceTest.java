package com.example.scmplatform.procurement.presentation.controller;

import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.PurchaseOrderView;
import com.example.scmplatform.procurement.domain.error.PoStatusTransitionInvalidException;
import com.example.scmplatform.procurement.domain.po.status.ActorType;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
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
 * {@link WebMvcTest} slice tests for {@link SupplierAckWebhookController}.
 *
 * <p>The supplier-ack webhook is a public endpoint (shared-secret verified inside
 * the controller). Security filter chain is bypassed via
 * {@code @AutoConfigureMockMvc(addFilters = false)}.
 *
 * <p>Test count: 4
 */
@WebMvcTest(SupplierAckWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, WebhookSignatureVerifier.class})
class SupplierAckWebhookControllerSliceTest {

    private static final String URL = "/api/procurement/webhooks/supplier-ack";
    private static final String WEBHOOK_SECRET = "scm-supplier-webhook-secret";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    PurchaseOrderApplicationService service;

    // ---- helpers ----

    private PurchaseOrderView acknowledgedView() {
        Instant now = Instant.now();
        return new PurchaseOrderView(
                "po-001", "scm", "PO-0001", "sup-001", "buyer-001",
                PoStatus.ACKNOWLEDGED, BigDecimal.TEN, "USD",
                now, now, null, null, now, now, List.of()
        );
    }

    private String validRequestJson() throws Exception {
        return objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {{
            put("tenantId", "scm");
            put("poId", "po-001");
            put("supplierAckRef", "ACK-REF-001");
        }});
    }

    // ---- tests ----

    @Test
    @DisplayName("POST /webhooks/supplier-ack — 200 happy path transitions to ACKNOWLEDGED")
    void ackHappyPath() throws Exception {
        when(service.acknowledge(any())).thenReturn(acknowledgedView());

        mockMvc.perform(post(URL)
                        .header("X-Supplier-Signature", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("po-001"))
                .andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"));
    }

    @Test
    @DisplayName("POST /webhooks/supplier-ack — 401 when signature is missing")
    void ackMissingSignature() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /webhooks/supplier-ack — 401 when signature is wrong")
    void ackWrongSignature() throws Exception {
        mockMvc.perform(post(URL)
                        .header("X-Supplier-Signature", "wrong-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /webhooks/supplier-ack — 422 PO_STATUS_TRANSITION_INVALID when PO in wrong state")
    void ackInvalidTransition() throws Exception {
        when(service.acknowledge(any()))
                .thenThrow(new PoStatusTransitionInvalidException(
                        PoStatus.DRAFT, PoStatus.ACKNOWLEDGED, ActorType.SUPPLIER));

        mockMvc.perform(post(URL)
                        .header("X-Supplier-Signature", WEBHOOK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PO_STATUS_TRANSITION_INVALID"))
                .andExpect(jsonPath("$.details.from").value("DRAFT"))
                .andExpect(jsonPath("$.details.to").value("ACKNOWLEDGED"));
    }
}
