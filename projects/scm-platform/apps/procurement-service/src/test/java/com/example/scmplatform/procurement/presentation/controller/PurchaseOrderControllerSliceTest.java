package com.example.scmplatform.procurement.presentation.controller;

import com.example.scmplatform.procurement.application.ActorContext;
import com.example.scmplatform.procurement.application.IdempotencyExecutor;
import com.example.scmplatform.procurement.application.IdempotencyHasher;
import com.example.scmplatform.procurement.application.PurchaseOrderApplicationService;
import com.example.scmplatform.procurement.application.PurchaseOrderView;
import com.example.scmplatform.procurement.application.command.DraftFromSuggestionCommand;
import com.example.scmplatform.procurement.application.command.DraftPurchaseOrderCommand;
import com.example.scmplatform.procurement.domain.error.PoNotFoundException;
import com.example.scmplatform.procurement.domain.po.PoOrigin;
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
import static org.mockito.ArgumentMatchers.anyInt;
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

    // TASK-BE-445: the controller now wraps mutations in IdempotencyExecutor. For the
    // slice we stub it as a pass-through (invoke the action Supplier) so these tests keep
    // asserting controller↔service behaviour; the executor's own logic is covered by
    // IdempotencyExecutorTest. The hasher is irrelevant here (returns a constant).
    @MockitoBean
    IdempotencyExecutor idempotency;

    @MockitoBean
    IdempotencyHasher hasher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void populateSecurityContext() {
        TestingAuthenticationToken auth =
                new TestingAuthenticationToken(BUYER, "credentials", "ROLE_BUYER");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(hasher.hash(any())).thenReturn("test-hash");
        when(idempotency.execute(anyString(), anyString(), anyString(), anyString(),
                anyInt(), any(), any()))
                .thenAnswer(inv -> ((java.util.function.Supplier<Object>) inv.getArgument(6)).get());
    }

    // ---- helpers ----

    private PurchaseOrderView draftView() {
        Instant now = Instant.now();
        return new PurchaseOrderView(
                "po-001", "scm", "PO-0001", "sup-001", "buyer-001",
                PoStatus.DRAFT, PoOrigin.OPERATOR, null, BigDecimal.TEN, "USD",
                null, null, null, null, now, now,
                List.of(new PurchaseOrderView.LineView(
                        "line-001", 1, "sku-001", "sup-sku-001",
                        BigDecimal.TEN, BigDecimal.ONE, BigDecimal.ZERO))
        );
    }

    private PurchaseOrderView fromSuggestionView() {
        Instant now = Instant.now();
        return new PurchaseOrderView(
                "po-dp-001", "scm", "PO-DP01", "sup-001", "operator-001",
                PoStatus.DRAFT, PoOrigin.DEMAND_PLANNING, "0192cccc-0000-0000-0000-000000000001",
                BigDecimal.ZERO, "KRW",
                null, null, null, null, now, now,
                List.of(new PurchaseOrderView.LineView(
                        "line-dp-001", 1, "SKU-APPLE-001", null,
                        BigDecimal.valueOf(100), BigDecimal.ZERO, BigDecimal.ZERO))
        );
    }

    private PurchaseOrderView submittedView() {
        Instant now = Instant.now();
        return new PurchaseOrderView(
                "po-001", "scm", "PO-0001", "sup-001", "buyer-001",
                PoStatus.SUBMITTED, PoOrigin.OPERATOR, null, BigDecimal.TEN, "USD",
                now, null, null, null, now, now, List.of()
        );
    }

    private PurchaseOrderView canceledView() {
        Instant now = Instant.now();
        return new PurchaseOrderView(
                "po-001", "scm", "PO-0001", "sup-001", "buyer-001",
                PoStatus.CANCELED, PoOrigin.OPERATOR, null, BigDecimal.TEN, "USD",
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

    // ---- POST /api/procurement/po/from-suggestion (ADR-MONO-027 D5) ----

    private String fromSuggestionRequestJson() throws Exception {
        return objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {{
            put("supplierId", "sup-001");
            put("currency", "KRW");
            put("origin", "DEMAND_PLANNING");
            put("sourceSuggestionId", "0192cccc-0000-0000-0000-000000000001");
            put("lines", List.of(new java.util.HashMap<String, Object>() {{
                put("lineNo", 1);
                put("sku", "SKU-APPLE-001");
                put("quantity", 100);
                put("unitPriceRef", "LAST_KNOWN");
            }}));
        }});
    }

    @Test
    @DisplayName("POST /po/from-suggestion — 201 DRAFT carrying origin + sourceSuggestionId, no Idempotency-Key header required")
    void fromSuggestionHappyPath() throws Exception {
        when(service.draftFromSuggestion(any(DraftFromSuggestionCommand.class)))
                .thenReturn(fromSuggestionView());

        // Deliberately NO Idempotency-Key header — this entry keys idempotency on
        // sourceSuggestionId, not the generic header.
        mockMvc.perform(post(BASE_URL + "/from-suggestion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(fromSuggestionRequestJson()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.id").value("po-dp-001"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.origin").value("DEMAND_PLANNING"))
                .andExpect(jsonPath("$.data.sourceSuggestionId")
                        .value("0192cccc-0000-0000-0000-000000000001"));
    }

    @Test
    @DisplayName("POST /po/from-suggestion — 400 VALIDATION_ERROR when origin is not DEMAND_PLANNING")
    void fromSuggestionRejectsForeignOrigin() throws Exception {
        String body = objectMapper.writeValueAsString(new java.util.HashMap<String, Object>() {{
            put("supplierId", "sup-001");
            put("currency", "KRW");
            put("origin", "OPERATOR");
            put("sourceSuggestionId", "0192cccc-0000-0000-0000-000000000001");
            put("lines", List.of(new java.util.HashMap<String, Object>() {{
                put("lineNo", 1);
                put("sku", "SKU-APPLE-001");
                put("quantity", 100);
            }}));
        }});

        mockMvc.perform(post(BASE_URL + "/from-suggestion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
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
                .andExpect(jsonPath("$.data.content[0].status").value("DRAFT"))
                // TASK-SCM-BE-020: monetary/quantity decimals serialise as
                // STRINGS per procurement-api.md (NOT JSON numbers) — the
                // platform-console PC-FE-008 consumer parses them as z.string().
                .andExpect(jsonPath("$.data.content[0].totalAmount")
                        .value(org.hamcrest.Matchers.instanceOf(String.class)))
                .andExpect(jsonPath("$.data.content[0].lines[0].quantity")
                        .value(org.hamcrest.Matchers.instanceOf(String.class)))
                .andExpect(jsonPath("$.data.content[0].lines[0].unitPrice")
                        .value(org.hamcrest.Matchers.instanceOf(String.class)));
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
