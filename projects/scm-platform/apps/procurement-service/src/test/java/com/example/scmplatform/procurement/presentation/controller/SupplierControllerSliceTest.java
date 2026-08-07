package com.example.scmplatform.procurement.presentation.controller;

import com.example.common.page.PageResult;
import com.example.scmplatform.procurement.application.ActorContext;
import com.example.scmplatform.procurement.application.IdempotencyExecutor;
import com.example.scmplatform.procurement.application.IdempotencyHasher;
import com.example.scmplatform.procurement.application.SupplierApplicationService;
import com.example.scmplatform.procurement.application.SupplierRegistration;
import com.example.scmplatform.procurement.application.SupplierView;
import com.example.scmplatform.procurement.domain.error.PermissionDeniedException;
import com.example.scmplatform.procurement.domain.error.SupplierNotFoundException;
import com.example.scmplatform.procurement.domain.supplier.SupplierStatus;
import com.example.scmplatform.procurement.presentation.advice.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice tests for {@link SupplierController} (TASK-SCM-BE-059).
 *
 * <p>Security filters are off; the {@link ActorContext} is put straight into the
 * {@link SecurityContextHolder}, matching {@link PurchaseOrderControllerSliceTest}.
 *
 * <p>The two status lines (201 create / 200 converge) are what these tests exist
 * for — they are the observable half of the contract's idempotency split.
 *
 * <p>Test count: 7
 */
@WebMvcTest(SupplierController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class SupplierControllerSliceTest {

    private static final String BASE_URL = "/api/procurement/suppliers";
    private static final ActorContext OPERATOR =
            new ActorContext("operator-001", "scm", Set.of("OPERATOR"));

    private static final String VALID_BODY = """
            {"code":"SUP-ACME-001","name":"ACME Components Co."}
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    SupplierApplicationService service;

    @MockitoBean
    IdempotencyExecutor idempotency;

    @MockitoBean
    IdempotencyHasher hasher;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void populateSecurityContext() {
        TestingAuthenticationToken auth =
                new TestingAuthenticationToken(OPERATOR, "credentials", "ROLE_OPERATOR");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(hasher.hash(any())).thenReturn("test-hash");
        when(idempotency.execute(anyString(), anyString(), anyString(), anyString(),
                anyInt(), any(), any()))
                .thenAnswer(inv -> ((java.util.function.Supplier<Object>) inv.getArgument(6)).get());
    }

    private SupplierView view() {
        Instant now = Instant.now();
        return new SupplierView("sup-001", "scm", "SUP-ACME-001", "ACME Components Co.",
                SupplierStatus.ACTIVE, null, null, now, now);
    }

    @Test
    @DisplayName("POST returns 201 when a row was inserted")
    void postReturns201OnCreate() throws Exception {
        when(service.register(any())).thenReturn(new SupplierRegistration(view(), true));

        mockMvc.perform(post(BASE_URL)
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("SUP-ACME-001"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("POST returns 200 (not 201, not 409) when the code already exists")
    void postReturns200OnConverge() throws Exception {
        when(service.register(any())).thenReturn(new SupplierRegistration(view(), false));

        mockMvc.perform(post(BASE_URL)
                        .header("Idempotency-Key", "different-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("sup-001"));
    }

    @Test
    @DisplayName("POST without Idempotency-Key returns 400 IDEMPOTENCY_KEY_REQUIRED")
    void postWithoutIdempotencyKey() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("POST with a lowercase code fails validation (422)")
    void postRejectsMalformedCode() throws Exception {
        mockMvc.perform(post(BASE_URL)
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"sup-acme\",\"name\":\"ACME\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST surfaces a use-case refusal as 403 PERMISSION_DENIED")
    void postSurfacesPermissionDenied() throws Exception {
        when(service.register(any()))
                .thenThrow(new PermissionDeniedException("OPERATOR actor required"));

        mockMvc.perform(post(BASE_URL)
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(VALID_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("GET /{id} returns 404 SUPPLIER_NOT_FOUND for an unknown id")
    void getUnknownSupplier() throws Exception {
        when(service.get(anyString(), any()))
                .thenThrow(new SupplierNotFoundException("Supplier not found: sup-x"));

        mockMvc.perform(get(BASE_URL + "/sup-x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("SUPPLIER_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET list returns the paginated envelope")
    void getListReturnsPage() throws Exception {
        when(service.search(any(), any(), any(), any()))
                .thenReturn(new PageResult<>(List.of(view()), 0, 20, 1, 1));

        mockMvc.perform(get(BASE_URL).param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].code").value("SUP-ACME-001"))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }
}
