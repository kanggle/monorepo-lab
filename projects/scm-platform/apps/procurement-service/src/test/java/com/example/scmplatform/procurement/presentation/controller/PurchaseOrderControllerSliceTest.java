package com.example.scmplatform.procurement.presentation.controller;

import com.example.scmplatform.procurement.application.ActorContext;
import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.PurchaseOrderView;
import com.example.scmplatform.procurement.application.command.DraftPurchaseOrderCommand;
import com.example.scmplatform.procurement.domain.error.PoNotFoundException;
import com.example.scmplatform.procurement.domain.error.PoStatusTransitionInvalidException;
import com.example.scmplatform.procurement.domain.error.SupplierNotFoundException;
import com.example.scmplatform.procurement.domain.error.SupplierUnavailableException;
import com.example.scmplatform.procurement.domain.po.status.ActorType;
import com.example.scmplatform.procurement.domain.po.status.PoStatus;
import com.example.scmplatform.procurement.presentation.advice.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice tests for {@link PurchaseOrderController}.
 *
 * <p>Security filter chain is bypassed via {@code @AutoConfigureMockMvc(addFilters = false)}.
 * The {@link ActorContext} is placed directly into the
 * {@link SecurityContextHolder} via a {@link TestingAuthenticationToken} so that
 * {@code ActorContextResolver.currentOrThrow()} resolves without a real JWT.
 *
 * <p>Test count: 10
 */
@WebMvcTest(PurchaseOrderController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class PurchaseOrderControllerSliceTest {

    private static final String BASE_URL = "/api/procurement/po";
    private static final ActorContext BUYER =
            new ActorContext("buyer-001", "scm", Set.of("BUYER"));
    private static final ActorContext OPERATOR =
            new ActorContext("operator-001", "scm", Set.of("OPERATOR"));

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    PurchaseOrderApplicationService service;

    @BeforeEach
    void populateSecurityContext() {
        TestingAuthenticationToken auth =
                new TestingAuthenticationToken(BUYER, "credentials", "ROLE_BUYER");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ---- helpers ----

    private PurchaseOrderView draftView() {
        Instant now = Instant.now();
        return new PurchaseOrderView(
                "po-001", "scm", "PO-0001", "sup-001", "buyer-001",
                PoStatus.DRAFT, BigDecimal.TEN, "USD",
                null, null, null, null, now, now,
                List.of(new PurchaseOrderView.LineView(
                        "line-001", 1, "sku-001", "sup-sku-001",
                        BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO))
        );
    }

    private PurchaseOrderView submittedView() {
        Instant now = Instant.now();
        return new PurchaseOrderView(
                "po-001", "scm", "PO-0001", "sup-001", "buyer-001",
                PoStatus.SUBMITTED, BigDecimal.TEN, "USD",
                now, null, null, null, now, now, List.of()
        );
    }

    private PurchaseOrderView canceledView() {
        Instant now = Instant.now();
        return new PurchaseOrderView(
                "po-001", "scm", "PO-0001", "sup-001", "buyer-001",
                PoStatus.CANCELED, BigDecimal.TEN, "USD",
                null, null, null, now, now, now, List.of()
        );
    }

    private String draftRequestJson() throws Exception {
        return objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {{
            put("supplierId", "sup-001");
            put("currency", "USD");
            put("lines", List.of(new java.util.HashMap<String, Object>() {{
                put("lineNo", 1);
                put("sku", "sku-001");
                put("supplierSku", "sup-sku-001");
                put("quantity", "10.00");
                put("unitPrice", "5.00");
            }}));
        }});
    }

    // ---- POST /api/procurement/po ----

    @Test
    @DisplayName("POST /po — 201 created with valid request")
    void draftHappyPath() throws Exception {
        when(service.draft(any(DraftPurchaseOrderCommand.class))).thenReturn(draftView());

        mockMvc.perform(post(BASE_URL)
                        .header("Idempotency-Key", "idem-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("po-001"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("POST /po — 400 when Idempotency-Key header missing")
    void draftMissingIdempotencyKey() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftRequestJson()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("POST /po — 404 SUPPLIER_NOT_FOUND when supplier does not exist")
    void draftSupplierNotFound() throws Exception {
        when(service.draft(any(DraftPurchaseOrderCommand.class)))
                .thenThrow(new SupplierNotFoundException("Supplier not found: sup-999"));

        mockMvc.perform(post(BASE_URL)
                        .header("Idempotency-Key", "idem-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(draftRequestJson()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUPPLIER_NOT_FOUND"));
    }

    // ---- GET /api/procurement/po/{poId} ----

    @Test
    @DisplayName("GET /po/{poId} — 200 with PO view")
    void getHappyPath() throws Exception {
        when(service.get(eq("po-001"), any(ActorContext.class))).thenReturn(draftView());

        mockMvc.perform(get(BASE_URL + "/po-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("po-001"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    @DisplayName("GET /po/{poId} — 404 PO_NOT_FOUND for unknown poId")
    void getNotFound() throws Exception {
        when(service.get(eq("missing"), any(ActorContext.class)))
                .thenThrow(new PoNotFoundException("PO not found: missing"));

        mockMvc.perform(get(BASE_URL + "/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PO_NOT_FOUND"));
    }

    // ---- GET /api/procurement/po ----

    @Test
    @DisplayName("GET /po — 200 with status filter applied")
    void searchWithStatusFilter() throws Exception {
        com.example.common.page.PageResult<PurchaseOrderView> pageResult =
                new com.example.common.page.PageResult<>(List.of(draftView()), 0, 20, 1L, 1);
        when(service.search(any(ActorContext.class), eq(PoStatus.DRAFT), isNull(), any()))
                .thenReturn(pageResult);

        mockMvc.perform(get(BASE_URL).param("status", "DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].status").value("DRAFT"));
    }

    @Test
    @DisplayName("GET /po — 400 VALIDATION_ERROR for invalid status enum value")
    void searchInvalidStatusEnum() throws Exception {
        mockMvc.perform(get(BASE_URL).param("status", "INVALID_STATUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ---- POST /api/procurement/po/{poId}/submit ----

    @Test
    @DisplayName("POST /po/{poId}/submit — 200 happy path transitions to SUBMITTED")
    void submitHappyPath() throws Exception {
        when(service.submit(any())).thenReturn(submittedView());

        mockMvc.perform(post(BASE_URL + "/po-001/submit")
                        .header("Idempotency-Key", "idem-submit-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
    }

    @Test
    @DisplayName("POST /po/{poId}/submit — 503 SUPPLIER_UNAVAILABLE when circuit is OPEN")
    void submitSupplierUnavailable() throws Exception {
        when(service.submit(any()))
                .thenThrow(new SupplierUnavailableException("circuit OPEN"));

        mockMvc.perform(post(BASE_URL + "/po-001/submit")
                        .header("Idempotency-Key", "idem-submit-002"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SUPPLIER_UNAVAILABLE"));
    }

    // ---- POST /api/procurement/po/{poId}/cancel ----

    @Test
    @DisplayName("POST /po/{poId}/cancel — 200 with reason body")
    void cancelWithReason() throws Exception {
        when(service.cancel(any())).thenReturn(canceledView());

        String body = objectMapper.writeValueAsString(
                new java.util.HashMap<String, Object>() {{ put("reason", "out of budget"); }});

        mockMvc.perform(post(BASE_URL + "/po-001/cancel")
                        .header("Idempotency-Key", "idem-cancel-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELED"));
    }

    @Test
    @DisplayName("POST /po/{poId}/cancel — 422 PO_STATUS_TRANSITION_INVALID for illegal transition")
    void cancelInvalidTransition() throws Exception {
        when(service.cancel(any()))
                .thenThrow(new PoStatusTransitionInvalidException(
                        PoStatus.SETTLED, PoStatus.CANCELED, ActorType.BUYER));

        mockMvc.perform(post(BASE_URL + "/po-001/cancel")
                        .header("Idempotency-Key", "idem-cancel-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("PO_STATUS_TRANSITION_INVALID"));
    }
}
