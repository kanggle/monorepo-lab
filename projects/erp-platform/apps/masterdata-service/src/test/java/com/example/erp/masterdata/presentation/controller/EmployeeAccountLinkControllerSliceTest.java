package com.example.erp.masterdata.presentation.controller;

import com.example.common.page.PageResult;
import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.application.MasterdataApplicationService;
import com.example.erp.masterdata.application.command.Commands.AcceptAccountLinkCommand;
import com.example.erp.masterdata.application.command.Commands.CreateEmployeeCommand;
import com.example.erp.masterdata.application.command.Commands.DeclineAccountLinkCommand;
import com.example.erp.masterdata.application.command.Commands.ProposeAccountLinkCommand;
import com.example.erp.masterdata.application.command.Commands.RevokeAccountLinkCommand;
import com.example.erp.masterdata.application.command.Commands.UnlinkAccountCommand;
import com.example.erp.masterdata.application.view.AuditDto;
import com.example.erp.masterdata.application.view.EffectivePeriodDto;
import com.example.erp.masterdata.application.view.EmployeeAccountLinkProposalView;
import com.example.erp.masterdata.application.view.EmployeeApproverRefView;
import com.example.erp.masterdata.application.view.EmployeeView;
import com.example.erp.masterdata.domain.error.DomainErrors;
import com.example.erp.masterdata.presentation.advice.GlobalExceptionHandler;
import com.example.erp.masterdata.presentation.support.IdempotentExecution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for the employee ↔ IAM account link endpoints (TASK-ERP-BE-044) on
 * {@link EmployeeController} + {@link AccountLinkProposalController}, with the
 * {@link GlobalExceptionHandler} envelope. Same harness as {@code DepartmentControllerSliceTest}:
 * security filters bypassed, {@link ActorContext} placed in the {@link SecurityContextHolder}
 * (the real filter chain + signed tokens are exercised by the integration test).
 */
@WebMvcTest({EmployeeController.class, AccountLinkProposalController.class})
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class EmployeeAccountLinkControllerSliceTest {

    private static final String BASE = "/api/erp/masterdata";
    private static final ActorContext ACTOR = new ActorContext("hr-1", "erp",
            Set.of("erp.write", "erp.read"), Set.of("*"));

    @Autowired MockMvc mockMvc;

    @MockitoBean MasterdataApplicationService service;
    @MockitoBean IdempotentExecution idempotency;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(ACTOR, "creds");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
        when(idempotency.run(anyString(), anyString(), anyString(), any(), any()))
                .thenAnswer(inv -> ((Supplier<ResponseEntity<?>>) inv.getArgument(4)).get());
    }

    private static EmployeeView employee(String accountId) {
        return new EmployeeView("emp-1", "E-1", "Hong", "dept-1", "cc-1", "jg-1", "ACTIVE",
                new EffectivePeriodDto(LocalDate.of(2026, 1, 1), null),
                new AuditDto(Instant.now(), Instant.now()), accountId);
    }

    private static EmployeeAccountLinkProposalView proposal(String status) {
        return new EmployeeAccountLinkProposalView("p-1", "emp-1", "acct-A", status, "hr-1",
                Instant.parse("2026-10-08T00:00:00Z"), null, null, null, null, null, null);
    }

    // ---- accountId on the employee views --------------------------------------------------

    @Test
    @DisplayName("AC-1: GET /employees/{id} — accountId ABSENT when unlinked (not null), effectiveTo still null")
    void accountIdAbsentWhenUnlinked() throws Exception {
        when(service.getEmployee(eq("emp-1"), any(), any())).thenReturn(employee(null));

        mockMvc.perform(get(BASE + "/employees/emp-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").doesNotExist())
                .andExpect(jsonPath("$.data.effectivePeriod.effectiveTo").value((Object) null));
    }

    @Test
    @DisplayName("AC-1: GET /employees/{id} — accountId present once linked")
    void accountIdPresentWhenLinked() throws Exception {
        when(service.getEmployee(eq("emp-1"), any(), any())).thenReturn(employee("acct-A"));

        mockMvc.perform(get(BASE + "/employees/emp-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value("acct-A"));
    }

    @Test
    @DisplayName("GET /employees/me routes to getMyEmployee, NOT to /{id} with id=\"me\"")
    void meRoute() throws Exception {
        when(service.getMyEmployee(any())).thenReturn(employee("hr-1"));

        mockMvc.perform(get(BASE + "/employees/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").value("hr-1"));
        verify(service, never()).getEmployee(eq("me"), any(), any());
    }

    @Test
    @DisplayName("AC-1: GET /employees/me with no link → 404 MASTERDATA_NOT_FOUND")
    void meNotLinked() throws Exception {
        when(service.getMyEmployee(any()))
                .thenThrow(new DomainErrors.MasterdataNotFoundException("no link"));

        mockMvc.perform(get(BASE + "/employees/me"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MASTERDATA_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /employees/{id}/approver-ref → {id,status,accountId?} only (no name)")
    void approverRef() throws Exception {
        when(service.getApproverRef(eq("emp-1"), any()))
                .thenReturn(new EmployeeApproverRefView("emp-1", "ACTIVE", null));

        mockMvc.perform(get(BASE + "/employees/emp-1/approver-ref"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("emp-1"))
                .andExpect(jsonPath("$.data.status").value("ACTIVE"))
                .andExpect(jsonPath("$.data.accountId").doesNotExist())
                .andExpect(jsonPath("$.data.name").doesNotExist());
    }

    @Test
    @DisplayName("POST /employees ignores an accountId in the body — the create command has no such field")
    void createIgnoresAccountId() throws Exception {
        when(service.createEmployee(any(CreateEmployeeCommand.class))).thenReturn(employee(null));

        mockMvc.perform(post(BASE + "/employees")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"employeeNumber\":\"E-1\",\"name\":\"Hong\",\"departmentId\":\"dept-1\","
                                + "\"costCenterId\":\"cc-1\",\"jobGradeId\":\"jg-1\",\"accountId\":\"acct-SMUGGLED\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accountId").doesNotExist());
    }

    // ---- write endpoints -------------------------------------------------------------------

    @Test
    @DisplayName("POST /employees/{id}/account-link-proposals → 201 PENDING; command carries path id + body")
    void propose() throws Exception {
        when(service.proposeAccountLink(any(ProposeAccountLinkCommand.class))).thenReturn(proposal("PENDING"));

        mockMvc.perform(post(BASE + "/employees/emp-1/account-link-proposals")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"acct-A\",\"reason\":\"onboarding\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("PENDING"))
                .andExpect(jsonPath("$.data.proposedBy").value("hr-1"))
                .andExpect(jsonPath("$.data.decidedBy").doesNotExist());

        ArgumentCaptor<ProposeAccountLinkCommand> c = ArgumentCaptor.forClass(ProposeAccountLinkCommand.class);
        verify(service).proposeAccountLink(c.capture());
        assertThat(c.getValue().employeeId()).isEqualTo("emp-1");
        assertThat(c.getValue().accountId()).isEqualTo("acct-A");
        assertThat(c.getValue().reason()).isEqualTo("onboarding");
    }

    @Test
    @DisplayName("propose: accountId over 64 chars / blank → 400 VALIDATION_ERROR (form only — no IAM lookup)")
    void proposeValidation() throws Exception {
        mockMvc.perform(post(BASE + "/employees/emp-1/account-link-proposals")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"" + "a".repeat(65) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post(BASE + "/employees/emp-1/account-link-proposals")
                        .header("Idempotency-Key", "k-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"  \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        verify(service, never()).proposeAccountLink(any());
    }

    @Test
    @DisplayName("propose without Idempotency-Key → 400 IDEMPOTENCY_KEY_REQUIRED")
    void proposeNeedsIdempotencyKey() throws Exception {
        mockMvc.perform(post(BASE + "/employees/emp-1/account-link-proposals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"acct-A\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REQUIRED"));
    }

    @Test
    @DisplayName("POST /account-link-proposals/{id}/accept with {} → 200 employee detail incl. accountId")
    void accept() throws Exception {
        when(service.acceptAccountLink(any(AcceptAccountLinkCommand.class))).thenReturn(employee("acct-A"));

        mockMvc.perform(post(BASE + "/account-link-proposals/p-1/accept")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("emp-1"))
                .andExpect(jsonPath("$.data.accountId").value("acct-A"));
    }

    @Test
    @DisplayName("AC-2: accept → SELF_ACCEPT maps to 403")
    void acceptSelf403() throws Exception {
        when(service.acceptAccountLink(any(AcceptAccountLinkCommand.class)))
                .thenThrow(new DomainErrors.EmployeeLinkSelfAcceptException("two-person"));

        mockMvc.perform(post(BASE + "/account-link-proposals/p-1/accept")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_LINK_SELF_ACCEPT"))
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @Test
    @DisplayName("AC-3: decline → NOT_ADDRESSEE maps to 403")
    void declineNotAddressee403() throws Exception {
        when(service.declineAccountLink(any(DeclineAccountLinkCommand.class)))
                .thenThrow(new DomainErrors.EmployeeLinkNotAddresseeException("not yours"));

        mockMvc.perform(post(BASE + "/account-link-proposals/p-1/decline")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"no\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_LINK_NOT_ADDRESSEE"));
    }

    @Test
    @DisplayName("AC-4: CONFLICT → 409 with details.cause")
    void conflict409WithCause() throws Exception {
        when(service.proposeAccountLink(any(ProposeAccountLinkCommand.class)))
                .thenThrow(new DomainErrors.EmployeeLinkConflictException("account_already_linked", "taken"));

        mockMvc.perform(post(BASE + "/employees/emp-1/account-link-proposals")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"acct-A\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_LINK_CONFLICT"))
                .andExpect(jsonPath("$.details.cause").value("account_already_linked"));
    }

    @Test
    @DisplayName("AC-5: INVALID → 422 with details.cause employee_not_active")
    void invalid422() throws Exception {
        when(service.proposeAccountLink(any(ProposeAccountLinkCommand.class)))
                .thenThrow(new DomainErrors.EmployeeLinkInvalidException("retired"));

        mockMvc.perform(post(BASE + "/employees/emp-1/account-link-proposals")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"accountId\":\"acct-A\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_LINK_INVALID"))
                .andExpect(jsonPath("$.details.cause").value("employee_not_active"));
    }

    @Test
    @DisplayName("PROPOSAL_NOT_FOUND → 404")
    void proposalNotFound404() throws Exception {
        when(service.revokeAccountLink(any(RevokeAccountLinkCommand.class)))
                .thenThrow(new DomainErrors.EmployeeLinkProposalNotFoundException("p-x"));

        mockMvc.perform(post(BASE + "/account-link-proposals/p-x/revoke")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"wrong\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("EMPLOYEE_LINK_PROPOSAL_NOT_FOUND"));
    }

    @Test
    @DisplayName("revoke / unlink require a reason → 400 VALIDATION_ERROR")
    void reasonRequired() throws Exception {
        mockMvc.perform(post(BASE + "/account-link-proposals/p-1/revoke")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post(BASE + "/employees/emp-1/account-link/unlink")
                        .header("Idempotency-Key", "k-2")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("POST /employees/{id}/account-link/unlink → 200 employee detail, accountId ABSENT")
    void unlink() throws Exception {
        when(service.unlinkAccount(any(UnlinkAccountCommand.class))).thenReturn(employee(null));

        mockMvc.perform(post(BASE + "/employees/emp-1/account-link/unlink")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"left\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accountId").doesNotExist());
    }

    // ---- list endpoints --------------------------------------------------------------------

    @Test
    @DisplayName("GET /account-link-proposals/mine → list envelope with employeeName / employeeNumber")
    void mine() throws Exception {
        EmployeeAccountLinkProposalView v = new EmployeeAccountLinkProposalView("p-1", "emp-1", "hr-1",
                "PENDING", "hr-2", Instant.parse("2026-10-08T00:00:00Z"), null, null, null, null, "Hong", "E-1");
        when(service.listMyPendingLinkProposals(any(), eq(0), eq(20)))
                .thenReturn(new PageResult<>(List.of(v), 0, 20, 1L, 1));

        mockMvc.perform(get(BASE + "/account-link-proposals/mine"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].employeeName").value("Hong"))
                .andExpect(jsonPath("$.data[0].employeeNumber").value("E-1"))
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    @Test
    @DisplayName("GET /employees/{id}/account-link-proposals → list envelope (history)")
    void history() throws Exception {
        when(service.listEmployeeLinkProposals(eq("emp-1"), any(), eq(0), eq(20)))
                .thenReturn(new PageResult<>(List.of(proposal("REVOKED")), 0, 20, 1L, 1));

        mockMvc.perform(get(BASE + "/employees/emp-1/account-link-proposals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].status").value("REVOKED"))
                .andExpect(jsonPath("$.data[0].employeeName").doesNotExist());
    }
}
