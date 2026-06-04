package com.example.erp.approval.presentation.controller;

import com.example.erp.approval.application.ActorContext;
import com.example.erp.approval.application.ApprovalApplicationService;
import com.example.erp.approval.application.command.Commands.ApproveCommand;
import com.example.erp.approval.application.command.Commands.CreateDraftCommand;
import com.example.erp.approval.application.command.Commands.SubmitCommand;
import com.example.erp.approval.application.view.ApprovalRequestView;
import com.example.erp.approval.domain.error.ApprovalErrors;
import com.example.erp.approval.presentation.advice.GlobalExceptionHandler;
import com.example.erp.approval.presentation.support.IdempotentExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link ApprovalRequestController} + the
 * {@link GlobalExceptionHandler} error envelope (every approval code → its HTTP
 * status). Security filters bypassed; {@link ActorContext} placed directly in
 * {@link SecurityContextHolder}.
 */
@WebMvcTest(ApprovalRequestController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ApprovalRequestControllerSliceTest {

    private static final ActorContext ACTOR = new ActorContext("emp-app", "erp",
            Set.of("erp.write"), Set.of("*"));

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ApprovalApplicationService service;
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

    private ApprovalRequestView view(String status) {
        return new ApprovalRequestView("appr-1", status, "DEPARTMENT", "dept-1",
                "title", "emp-app", "emp-sub", null, List.of(),
                Instant.now(), null, null);
    }

    @Test
    @DisplayName("POST /requests → 201 + DRAFT, submittedAt/finalizedAt ABSENT")
    void createReturns201() throws Exception {
        when(service.createDraft(any(CreateDraftCommand.class))).thenReturn(view("DRAFT"));

        mockMvc.perform(post("/api/erp/approval/requests")
                        .header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectType\":\"DEPARTMENT\",\"subjectId\":\"dept-1\","
                                + "\"title\":\"t\",\"approverId\":\"emp-app\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.submittedAt").doesNotExist())
                .andExpect(jsonPath("$.data.finalizedAt").doesNotExist())
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("submit illegal transition → 409 APPROVAL_STATUS_TRANSITION_INVALID")
    void submitInvalid409() throws Exception {
        when(service.submit(any(SubmitCommand.class)))
                .thenThrow(new ApprovalErrors.ApprovalStatusTransitionInvalidException("nope"));

        mockMvc.perform(post("/api/erp/approval/requests/appr-1/submit")
                        .header("Idempotency-Key", "k1"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPROVAL_STATUS_TRANSITION_INVALID"));
    }

    @Test
    @DisplayName("submit subject unresolved → 422 APPROVAL_ROUTE_INVALID")
    void submitRouteInvalid422() throws Exception {
        when(service.submit(any(SubmitCommand.class)))
                .thenThrow(new ApprovalErrors.ApprovalRouteInvalidException("subject"));

        mockMvc.perform(post("/api/erp/approval/requests/appr-1/submit")
                        .header("Idempotency-Key", "k1"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("APPROVAL_ROUTE_INVALID"));
    }

    @Test
    @DisplayName("approve by non-approver → 403 APPROVAL_NOT_AUTHORIZED_APPROVER")
    void approveNotAuthorized403() throws Exception {
        when(service.approve(any(ApproveCommand.class)))
                .thenThrow(new ApprovalErrors.ApprovalNotAuthorizedApproverException("wrong"));

        mockMvc.perform(post("/api/erp/approval/requests/appr-1/approve")
                        .header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("APPROVAL_NOT_AUTHORIZED_APPROVER"));
    }

    @Test
    @DisplayName("approve finalized → 409 APPROVAL_ALREADY_FINALIZED")
    void approveFinalized409() throws Exception {
        when(service.approve(any(ApproveCommand.class)))
                .thenThrow(new ApprovalErrors.ApprovalAlreadyFinalizedException("done"));

        mockMvc.perform(post("/api/erp/approval/requests/appr-1/approve")
                        .header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("APPROVAL_ALREADY_FINALIZED"));
    }

    @Test
    @DisplayName("detail unknown → 404 APPROVAL_REQUEST_NOT_FOUND")
    void detailNotFound404() throws Exception {
        when(service.detail(anyString(), any(ActorContext.class)))
                .thenThrow(new ApprovalErrors.ApprovalRequestNotFoundException("missing"));

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .get("/api/erp/approval/requests/missing"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("APPROVAL_REQUEST_NOT_FOUND"));
    }

    @Test
    @DisplayName("create missing Idempotency-Key → 400 IDEMPOTENCY_KEY_REQUIRED")
    void missingIdempotencyKey400() throws Exception {
        mockMvc.perform(post("/api/erp/approval/requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subjectType\":\"DEPARTMENT\",\"subjectId\":\"dept-1\","
                                + "\"title\":\"t\",\"approverId\":\"emp-app\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("reject without reason → 400 VALIDATION_ERROR")
    void rejectNoReason400() throws Exception {
        mockMvc.perform(post("/api/erp/approval/requests/appr-1/reject")
                        .header("Idempotency-Key", "k1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
