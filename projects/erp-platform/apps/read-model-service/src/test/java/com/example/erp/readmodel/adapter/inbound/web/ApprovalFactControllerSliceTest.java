package com.example.erp.readmodel.adapter.inbound.web;

import com.example.erp.readmodel.adapter.inbound.web.advice.GlobalExceptionHandler;
import com.example.erp.readmodel.application.QueryApprovalFactUseCase;
import com.example.erp.readmodel.application.query.ApprovalFactPage;
import com.example.erp.readmodel.domain.approval.ApprovalFactProjection;
import com.example.erp.readmodel.domain.approval.ApprovalFactView;
import com.example.erp.readmodel.domain.approval.ApprovalStatus;
import com.example.erp.readmodel.domain.approval.ApprovalSubjectType;
import com.example.erp.readmodel.domain.error.ReadModelNotFoundException;
import com.example.erp.readmodel.presentation.security.OrgScope;
import com.example.erp.readmodel.presentation.security.ReadAccessDeniedException;
import com.example.erp.readmodel.presentation.security.ReadAuthorizationGate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link WebMvcTest} slice for {@link ApprovalFactController} + the
 * {@link GlobalExceptionHandler} error envelope. Security filters bypassed; the
 * READ gate is mocked. Asserts the list envelope + filters, the detail 404
 * (projection miss / out-of-scope), the {@code meta.unresolved} subject shape,
 * and the NON_NULL absent timestamp fields.
 */
@WebMvcTest(ApprovalFactController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class ApprovalFactControllerSliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    QueryApprovalFactUseCase useCase;
    @MockitoBean
    ReadAuthorizationGate readGate;

    @BeforeEach
    void platformScopeByDefault() {
        lenient().when(readGate.orgScope(any())).thenReturn(OrgScope.platform());
    }

    private ApprovalFactView submittedDeptView() {
        ApprovalFactProjection fact = ApprovalFactProjection.ofSubmitted(
                "appr-1", ApprovalSubjectType.DEPARTMENT, "dept-1", "emp-appr", "emp-sub",
                Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"),
                "evt-1");
        return ApprovalFactView.ofDepartment(fact,
                new ApprovalFactView.DepartmentSubjectRef("dept-1", "SALES", "영업본부",
                        List.of(new ApprovalFactView.PathNode("dept-1", "SALES", "영업본부"))));
    }

    @Test
    void getOneReturnsEnvelopeWithWarningAndAbsentFinalizedAt() throws Exception {
        when(useCase.getOne("appr-1", null)).thenReturn(submittedDeptView());

        mockMvc.perform(get("/api/erp/read-model/approvals/appr-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.approvalRequestId").value("appr-1"))
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.subject.code").value("SALES"))
                .andExpect(jsonPath("$.data.submittedAt").exists())
                .andExpect(jsonPath("$.data.finalizedAt").doesNotExist())
                .andExpect(jsonPath("$.data.lastReason").doesNotExist())
                .andExpect(jsonPath("$.meta.warning").value("Eventually-consistent read-model"))
                .andExpect(jsonPath("$.meta.unresolved").doesNotExist());
    }

    @Test
    void getOneUnresolvedSubjectAddsMetaUnresolved() throws Exception {
        ApprovalFactProjection fact = ApprovalFactProjection.ofTerminal(
                "appr-2", ApprovalStatus.APPROVED, ApprovalSubjectType.EMPLOYEE, "emp-x",
                "emp-appr", "emp-sub", Instant.parse("2026-01-02T00:00:00Z"), null,
                Instant.parse("2026-01-02T00:00:00Z"), "evt-2");
        when(useCase.getOne("appr-2", null))
                .thenReturn(ApprovalFactView.ofUnresolvedSubject(fact));

        mockMvc.perform(get("/api/erp/read-model/approvals/appr-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subject").doesNotExist())
                .andExpect(jsonPath("$.data.finalizedAt").exists())
                .andExpect(jsonPath("$.meta.unresolved[0]").value("subject"));
    }

    @Test
    void getOneProjectionMissOrOutOfScopeReturns404() throws Exception {
        when(useCase.getOne("ghost", null)).thenThrow(new ReadModelNotFoundException("ghost"));

        mockMvc.perform(get("/api/erp/read-model/approvals/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MASTERDATA_NOT_FOUND"));
    }

    @Test
    void listReturnsPagedEnvelopeWithFilters() throws Exception {
        when(useCase.list(eq(ApprovalStatus.SUBMITTED), eq(ApprovalSubjectType.DEPARTMENT),
                isNull(), isNull(), isNull(), isNull(), anyInt(), anyInt()))
                .thenReturn(new ApprovalFactPage(List.of(submittedDeptView()), 0, 20, 1L));

        mockMvc.perform(get("/api/erp/read-model/approvals"
                        + "?status=SUBMITTED&subjectType=DEPARTMENT&page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].approvalRequestId").value("appr-1"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.meta.warning").value("Eventually-consistent read-model"));
    }

    @Test
    void readGateDenialReturns403PermissionDenied() throws Exception {
        doThrow(new ReadAccessDeniedException("no read")).when(readGate).requireRead(any());

        mockMvc.perform(get("/api/erp/read-model/approvals/appr-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void badStatusReturns400ValidationError() throws Exception {
        mockMvc.perform(get("/api/erp/read-model/approvals?status=BOGUS"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void badSizeReturns400ValidationError() throws Exception {
        mockMvc.perform(get("/api/erp/read-model/approvals?size=999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
