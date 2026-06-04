package com.example.erp.readmodel.adapter.inbound.web;

import com.example.erp.readmodel.adapter.inbound.web.advice.GlobalExceptionHandler;
import com.example.erp.readmodel.application.QueryEmployeeOrgViewUseCase;
import com.example.erp.readmodel.application.query.EmployeeOrgViewPage;
import com.example.erp.readmodel.domain.common.MasterStatus;
import com.example.erp.readmodel.domain.error.ReadModelNotFoundException;
import com.example.erp.readmodel.domain.orgview.EmployeeOrgView;
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

import java.time.LocalDate;
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
 * {@link WebMvcTest} slice for {@link EmployeeOrgViewController} + the
 * {@link GlobalExceptionHandler} error envelope. Security filters bypassed; the
 * READ gate is mocked. Asserts the {@code meta.warning} + {@code meta.unresolved}
 * shape and the 404 {@code MASTERDATA_NOT_FOUND} projection-miss path.
 */
@WebMvcTest(EmployeeOrgViewController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class EmployeeOrgViewControllerSliceTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    QueryEmployeeOrgViewUseCase useCase;
    @MockitoBean
    ReadAuthorizationGate readGate;

    @BeforeEach
    void platformScopeByDefault() {
        // Default = platform scope (no org_scope narrowing); the controller maps
        // this to a null orgScopeRootIds passed to the use case (net-zero).
        lenient().when(readGate.orgScope(any())).thenReturn(OrgScope.platform());
    }

    private EmployeeOrgView resolvedView() {
        return new EmployeeOrgView.Builder("emp-1", "E-1001", "홍길동", MasterStatus.ACTIVE,
                LocalDate.parse("2026-01-01"), null)
                .department(new EmployeeOrgView.DepartmentRef("dept-1", "SALES", "영업본부",
                        List.of(new EmployeeOrgView.PathNode("hq", "HQ", "본사"),
                                new EmployeeOrgView.PathNode("dept-1", "SALES", "영업본부"))), false)
                .costCenter(new EmployeeOrgView.CostCenterRef("cc-1", "CC-100", "영업원가센터"), false)
                .jobGrade(new EmployeeOrgView.JobGradeRef("jg-1", "G3", "사원", 30), false)
                .build();
    }

    @Test
    void getOneReturnsEnvelopeWithWarningAndResolvedRefs() throws Exception {
        when(useCase.getOne("emp-1", null)).thenReturn(resolvedView());

        mockMvc.perform(get("/api/erp/read-model/employees/emp-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value("emp-1"))
                .andExpect(jsonPath("$.data.department.code").value("SALES"))
                .andExpect(jsonPath("$.data.department.path[0].code").value("HQ"))
                .andExpect(jsonPath("$.data.costCenter.code").value("CC-100"))
                .andExpect(jsonPath("$.data.jobGrade.displayOrder").value(30))
                .andExpect(jsonPath("$.meta.warning").value("Eventually-consistent read-model"))
                .andExpect(jsonPath("$.meta.unresolved").doesNotExist());
    }

    @Test
    void getOneWithUnresolvedAddsMetaUnresolved() throws Exception {
        EmployeeOrgView view = new EmployeeOrgView.Builder("emp-2", "E-1002", "김철수",
                MasterStatus.ACTIVE, null, null)
                .department(null, true)
                .costCenter(new EmployeeOrgView.CostCenterRef("cc-1", "CC-100", "cc"), false)
                .jobGrade(null, true)
                .build();
        when(useCase.getOne("emp-2", null)).thenReturn(view);

        mockMvc.perform(get("/api/erp/read-model/employees/emp-2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.department").doesNotExist())
                .andExpect(jsonPath("$.meta.unresolved").isArray())
                .andExpect(jsonPath("$.meta.unresolved[0]").value("department"))
                .andExpect(jsonPath("$.meta.unresolved[1]").value("jobGrade"));
    }

    @Test
    void getOneProjectionMissReturns404() throws Exception {
        when(useCase.getOne("ghost", null)).thenThrow(new ReadModelNotFoundException("ghost"));

        mockMvc.perform(get("/api/erp/read-model/employees/ghost"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MASTERDATA_NOT_FOUND"));
    }

    @Test
    void listReturnsPagedEnvelopeWithWarning() throws Exception {
        when(useCase.list(eq(MasterStatus.ACTIVE), any(), isNull(), anyInt(), anyInt()))
                .thenReturn(new EmployeeOrgViewPage(List.of(resolvedView()), 0, 20, 1L));

        mockMvc.perform(get("/api/erp/read-model/employees?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("emp-1"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.totalElements").value(1))
                .andExpect(jsonPath("$.meta.warning").value("Eventually-consistent read-model"));
    }

    @Test
    void readGateDenialReturns403PermissionDenied() throws Exception {
        doThrow(new ReadAccessDeniedException("no read")).when(readGate).requireRead(any());

        mockMvc.perform(get("/api/erp/read-model/employees/emp-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }

    @Test
    void badSizeReturns400ValidationError() throws Exception {
        mockMvc.perform(get("/api/erp/read-model/employees?size=999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
