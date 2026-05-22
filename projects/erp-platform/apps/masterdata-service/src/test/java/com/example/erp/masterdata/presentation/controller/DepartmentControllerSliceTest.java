package com.example.erp.masterdata.presentation.controller;

import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.application.MasterdataApplicationService;
import com.example.erp.masterdata.application.command.Commands.CreateDepartmentCommand;
import com.example.erp.masterdata.application.view.DepartmentView;
import com.example.erp.masterdata.domain.error.DomainErrors;
import com.example.erp.masterdata.presentation.advice.GlobalExceptionHandler;
import com.example.erp.masterdata.presentation.support.IdempotentExecution;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link DepartmentController} + the
 * {@link GlobalExceptionHandler} error envelope. Security filters bypassed;
 * {@link ActorContext} placed directly in {@link SecurityContextHolder}.
 */
@WebMvcTest(DepartmentController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class DepartmentControllerSliceTest {

    private static final ActorContext ACTOR = new ActorContext("user-1", "erp",
            Set.of("erp.write"), Set.of("*"));

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @MockitoBean
    MasterdataApplicationService service;
    @MockitoBean
    IdempotentExecution idempotency;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(ACTOR, "creds");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(idempotency.run(anyString(), anyString(), anyString(), any(), any()))
                .thenAnswer(inv -> ((Supplier<ResponseEntity<?>>) inv.getArgument(4)).get());
    }

    @Test
    @DisplayName("POST /departments returns 201 + envelope with data.code")
    void createReturns201() throws Exception {
        DepartmentView v = new DepartmentView("d-1", "DEPT-1", "Sales", null,
                "ACTIVE", LocalDate.of(2026, 1, 1), null,
                Instant.now(), Instant.now());
        when(service.createDepartment(any(CreateDepartmentCommand.class))).thenReturn(v);

        mockMvc.perform(post("/api/erp/masterdata/departments")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DEPT-1\",\"name\":\"Sales\","
                                + "\"effectiveFrom\":\"2026-01-01\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.code").value("DEPT-1"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("DuplicateKey → 409 MASTERDATA_DUPLICATE_KEY envelope")
    void duplicateKey409() throws Exception {
        when(service.createDepartment(any(CreateDepartmentCommand.class)))
                .thenThrow(new DomainErrors.MasterdataDuplicateKeyException("dup"));

        mockMvc.perform(post("/api/erp/masterdata/departments")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DEPT-1\",\"name\":\"Sales\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MASTERDATA_DUPLICATE_KEY"));
    }

    @Test
    @DisplayName("ReferenceViolation → 409 MASTERDATA_REFERENCE_VIOLATION")
    void referenceViolation409() throws Exception {
        when(service.createDepartment(any(CreateDepartmentCommand.class)))
                .thenThrow(new DomainErrors.MasterdataReferenceViolationException("ref"));

        mockMvc.perform(post("/api/erp/masterdata/departments")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DEPT-1\",\"name\":\"Sales\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MASTERDATA_REFERENCE_VIOLATION"));
    }

    @Test
    @DisplayName("PermissionDenied → 403 PERMISSION_DENIED")
    void permissionDenied403() throws Exception {
        when(service.createDepartment(any(CreateDepartmentCommand.class)))
                .thenThrow(new DomainErrors.PermissionDeniedException("no role"));

        mockMvc.perform(post("/api/erp/masterdata/departments")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DEPT-1\",\"name\":\"Sales\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    @DisplayName("Missing Idempotency-Key → 400 IDEMPOTENCY_KEY_REQUIRED")
    void missingIdempotencyKey400() throws Exception {
        mockMvc.perform(post("/api/erp/masterdata/departments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"code\":\"DEPT-1\",\"name\":\"Sales\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("Validation error → 400 VALIDATION_ERROR")
    void validation400() throws Exception {
        mockMvc.perform(post("/api/erp/masterdata/departments")
                        .header("Idempotency-Key", "key-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Sales\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
