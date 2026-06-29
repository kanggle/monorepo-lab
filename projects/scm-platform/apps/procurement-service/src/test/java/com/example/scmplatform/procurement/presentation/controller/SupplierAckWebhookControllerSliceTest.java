package com.example.scmplatform.procurement.presentation.controller;

import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.PurchaseOrderView;
import com.example.scmplatform.procurement.domain.error.PoStatusTransitionInvalidException;
import com.example.scmplatform.procurement.domain.po.PoOrigin;
import com.example.scmplatform.procurement.domain.po.status.ActorType;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
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
 * <p>The supplier-ack webhook is a public endpoint. HMAC-SHA256 signature +
 * timestamp + replay verification is now enforced by {@code WebhookSignatureFilter}
 * (a servlet filter), which is out of scope for this controller slice — the
 * filter chain is bypassed via {@code @AutoConfigureMockMvc(addFilters = false)},
 * so these tests cover the controller's request binding + business mapping only.
 *
 * <p>Test count: 2
 */
@WebMvcTest(SupplierAckWebhookController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SupplierAckWebhookControllerSliceTest {

    private static final String URL = "/api/procurement/webhooks/supplier-ack";

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
                PoStatus.ACKNOWLEDGED, PoOrigin.OPERATOR, null, BigDecimal.TEN, "USD",
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
    @DisplayName("POST /webhooks/supplier-ack — 200 happy path transitions to ACKNOWLEDGED (signature enforced by filter, out of slice)")
    void ackHappyPath() throws Exception {
        when(service.acknowledge(any())).thenReturn(acknowledgedView());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("po-001"))
                .andExpect(jsonPath("$.data.status").value("ACKNOWLEDGED"));
    }

    @Test
    @DisplayName("POST /webhooks/supplier-ack — 422 PO_STATUS_TRANSITION_INVALID when PO in wrong state")
    void ackInvalidTransition() throws Exception {
        when(service.acknowledge(any()))
                .thenThrow(new PoStatusTransitionInvalidException(
                        PoStatus.DRAFT, PoStatus.ACKNOWLEDGED, ActorType.SUPPLIER));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequestJson()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PO_STATUS_TRANSITION_INVALID"))
                .andExpect(jsonPath("$.details.from").value("DRAFT"))
                .andExpect(jsonPath("$.details.to").value("ACKNOWLEDGED"));
    }
}
