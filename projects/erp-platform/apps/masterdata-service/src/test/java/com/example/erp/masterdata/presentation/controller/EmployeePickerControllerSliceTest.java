package com.example.erp.masterdata.presentation.controller;

import com.example.common.page.PageResult;
import com.example.erp.masterdata.application.ActorContext;
import com.example.erp.masterdata.application.MasterdataApplicationService;
import com.example.erp.masterdata.application.event.MasterdataEventPublisher;
import com.example.erp.masterdata.application.port.outbound.ClockPort;
import com.example.erp.masterdata.domain.audit.AuditLogRepository;
import com.example.erp.masterdata.domain.businesspartner.repository.BusinessPartnerRepository;
import com.example.erp.masterdata.domain.costcenter.repository.CostCenterRepository;
import com.example.erp.masterdata.domain.department.Department;
import com.example.erp.masterdata.domain.department.repository.DepartmentRepository;
import com.example.erp.masterdata.domain.effectivedate.EffectivePeriod;
import com.example.erp.masterdata.domain.employee.Employee;
import com.example.erp.masterdata.domain.employee.link.repository.EmployeeAccountLinkProposalRepository;
import com.example.erp.masterdata.domain.employee.repository.EmployeeRepository;
import com.example.erp.masterdata.domain.jobgrade.repository.JobGradeRepository;
import com.example.erp.masterdata.infrastructure.authorization.RoleScopeAuthorizationAdapter;
import com.example.erp.masterdata.presentation.advice.GlobalExceptionHandler;
import com.example.erp.masterdata.presentation.support.IdempotentExecution;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-ERP-BE-045 — {@code GET /api/erp/masterdata/employees/picker} over HTTP with the REAL
 * {@link MasterdataApplicationService} and the REAL {@link RoleScopeAuthorizationAdapter} behind
 * {@link EmployeeController} (only the repositories are mocks). The other employee slices mock the service,
 * which would make an authorization control group here meaningless: the verdict lives in the service + adapter.
 *
 * <p>Same harness otherwise as {@code EmployeeAccountLinkControllerSliceTest}: security filters bypassed,
 * {@link ActorContext} placed in the {@link SecurityContextHolder} (the signed-token filter chain is covered by
 * the integration suites).
 */
@WebMvcTest(EmployeeController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({GlobalExceptionHandler.class, EmployeePickerControllerSliceTest.RealServiceConfig.class})
class EmployeePickerControllerSliceTest {

    private static final String URL = "/api/erp/masterdata/employees/picker";
    private static final String TENANT = "erp";
    private static final Instant NOW = Instant.parse("2026-10-20T03:00:00Z");
    private static final LocalDate FROM = LocalDate.of(2020, 1, 1);

    /** The element, exactly (masterdata-api.md § GET /employees/picker). */
    private static final Set<String> PICKER_KEYS = Set.of("id", "employeeNumber", "name", "departmentId",
            "departmentName", "status", "accountLinked");

    @TestConfiguration
    static class RealServiceConfig {
        @Bean
        MasterdataApplicationService masterdataApplicationService(
                DepartmentRepository departmentRepository, EmployeeRepository employeeRepository,
                JobGradeRepository jobGradeRepository, CostCenterRepository costCenterRepository,
                BusinessPartnerRepository businessPartnerRepository, AuditLogRepository auditLogRepository,
                ClockPort clock, MasterdataEventPublisher eventPublisher,
                EmployeeAccountLinkProposalRepository linkProposalRepository) {
            return new MasterdataApplicationService(departmentRepository, employeeRepository, jobGradeRepository,
                    costCenterRepository, businessPartnerRepository, auditLogRepository,
                    new RoleScopeAuthorizationAdapter(departmentRepository), clock, eventPublisher,
                    new ObjectMapper(), linkProposalRepository);
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean DepartmentRepository departmentRepository;
    @MockitoBean EmployeeRepository employeeRepository;
    @MockitoBean JobGradeRepository jobGradeRepository;
    @MockitoBean CostCenterRepository costCenterRepository;
    @MockitoBean BusinessPartnerRepository businessPartnerRepository;
    @MockitoBean AuditLogRepository auditLogRepository;
    @MockitoBean ClockPort clock;
    @MockitoBean MasterdataEventPublisher eventPublisher;
    @MockitoBean EmployeeAccountLinkProposalRepository linkProposalRepository;
    @MockitoBean IdempotentExecution idempotency;

    private Employee kim;
    private Employee lee;

    @BeforeEach
    void setUp() {
        kim = Employee.create("emp-1", TENANT, "E-1001", "Kim", "dept-sales", "cc-1", "jg-1",
                EffectivePeriod.openEnded(FROM), NOW);
        kim.linkAccount("acct-kim", NOW);
        lee = Employee.create("emp-2", TENANT, "E-1002", "Lee", "dept-unknown", "cc-1", "jg-1",
                EffectivePeriod.openEnded(FROM), NOW);
        Department sales = Department.create("dept-sales", TENANT, "SALES", "영업본부", null,
                EffectivePeriod.openEnded(FROM), NOW);
        when(clock.now()).thenReturn(NOW);
        when(employeeRepository.findPickerPage(eq(TENANT), any(), isNull(), eq(0), eq(20)))
                .thenReturn(new PageResult<>(List.of(kim, lee), 0, 20, 2L, 1));
        when(departmentRepository.findAllByIds(anyCollection(), eq(TENANT))).thenReturn(List.of(sales));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void as(Set<String> roles, Set<String> orgScope) {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(
                new ActorContext("op-1", TENANT, roles, orgScope, Set.of("erp")), "creds");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ---- AC-2 control group --------------------------------------------------------------------------

    @Test
    @DisplayName("🔴 BE-045 AC-2: no role → 403 PERMISSION_DENIED")
    void noRole403() throws Exception {
        as(Set.of(), Set.of("*"));
        mockMvc.perform(get(URL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        verify(employeeRepository, never()).findPickerPage(anyString(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("🔴 BE-045 AC-2: ERP_APPROVAL_READ only (+ entry role) → 403 PERMISSION_DENIED")
    void approvalRead403() throws Exception {
        as(Set.of("ERP_OPERATOR", "ERP_APPROVAL_READ"), Set.of("*"));
        mockMvc.perform(get(URL))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        verify(employeeRepository, never()).findPickerPage(anyString(), any(), any(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("🔴 BE-045 AC-2: ERP_APPROVAL_WRITE → 200, meta.scope TENANT for org_scope '*'")
    void approvalWrite200() throws Exception {
        as(Set.of("ERP_OPERATOR", "ERP_APPROVAL_READ", "ERP_APPROVAL_WRITE"), Set.of("*"));
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value("emp-1"))
                .andExpect(jsonPath("$.meta.scope").value("TENANT"))
                .andExpect(jsonPath("$.meta.page").value(0))
                .andExpect(jsonPath("$.meta.size").value(20))
                .andExpect(jsonPath("$.meta.totalElements").value(2))
                .andExpect(jsonPath("$.meta.totalPages").value(1))
                .andExpect(jsonPath("$.meta.timestamp").exists());
    }

    @Test
    @DisplayName("BE-045 AC-2: ERP_MASTERDATA_READ → 200")
    void masterdataRead200() throws Exception {
        as(Set.of("ERP_OPERATOR", "ERP_MASTERDATA_READ"), Set.of("*"));
        mockMvc.perform(get(URL)).andExpect(status().isOk());
    }

    // ---- element shape ---------------------------------------------------------------------------------

    @Test
    @DisplayName("🔴 BE-045 AC-2: element key set == the 7 picker keys (no costCenterId/jobGradeId/accountId/effectivePeriod/version)")
    void elementKeySetExact() throws Exception {
        as(Set.of("ERP_APPROVAL_WRITE"), Set.of("*"));
        String body = mockMvc.perform(get(URL)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode data = objectMapper.readTree(body).get("data");

        assertThat(keys(data.get(0))).isEqualTo(PICKER_KEYS);
        assertThat(data.get(0).get("accountLinked").isBoolean()).isTrue();
        assertThat(data.get(0).get("accountLinked").booleanValue()).isTrue();
        assertThat(data.get(0).get("departmentName").asText()).isEqualTo("영업본부");
        // Unresolved department → departmentName ABSENT (not null); the rest unchanged.
        Set<String> withoutName = new HashSet<>(PICKER_KEYS);
        withoutName.remove("departmentName");
        assertThat(keys(data.get(1))).isEqualTo(withoutName);
        assertThat(data.get(1).get("accountLinked").booleanValue()).isFalse();
        // The account id itself never leaves the service.
        assertThat(body).doesNotContain("acct-kim");
    }

    @Test
    @DisplayName("BE-045: ?active=true is passed to the repository as ACTIVE-only")
    void activeFilterForwarded() throws Exception {
        as(Set.of("ERP_APPROVAL_WRITE"), Set.of("*"));
        when(employeeRepository.findPickerPage(eq(TENANT), eq(Boolean.TRUE), isNull(), eq(0), eq(20)))
                .thenReturn(new PageResult<>(List.of(kim), 0, 20, 1L, 1));
        mockMvc.perform(get(URL).param("active", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.totalElements").value(1));
    }

    // ---- scope over the wire ---------------------------------------------------------------------------

    @Test
    @DisplayName("BE-045: empty org_scope → 200 empty data, meta.scope SUBTREE")
    void emptyScopeSubtree() throws Exception {
        as(Set.of("ERP_APPROVAL_WRITE"), Set.of());
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.meta.scope").value("SUBTREE"))
                .andExpect(jsonPath("$.meta.totalElements").value(0));
    }

    @Test
    @DisplayName("BE-045: root scope → meta.scope SUBTREE and the repository gets the expanded subtree")
    void rootScopeSubtree() throws Exception {
        as(Set.of("ERP_APPROVAL_WRITE"), Set.of("dept-A"));
        when(departmentRepository.subtreeIds(Set.of("dept-A"), TENANT)).thenReturn(Set.of("dept-A", "dept-A1"));
        when(employeeRepository.findPickerPage(eq(TENANT), isNull(), eq(Set.of("dept-A", "dept-A1")), eq(0), eq(20)))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.scope").value("SUBTREE"));
    }

    // ---- routing -------------------------------------------------------------------------------------

    @Test
    @DisplayName("BE-045: /employees/picker is NOT parsed as /employees/{id} with id=\"picker\"")
    void pickerIsNotAnId() throws Exception {
        as(Set.of("ERP_APPROVAL_WRITE"), Set.of("*"));
        mockMvc.perform(get(URL)).andExpect(status().isOk());
        verify(employeeRepository, never()).findById(eq("picker"), anyString());
    }

    @Test
    @DisplayName("BE-045 control: the existing list envelope carries NO meta.scope")
    void existingListHasNoScope() throws Exception {
        as(Set.of("ERP_MASTERDATA_READ"), Set.of("*"));
        when(employeeRepository.findAll(eq(TENANT), any(), eq(0), eq(20)))
                .thenReturn(new PageResult<>(List.of(), 0, 20, 0L, 0));
        mockMvc.perform(get("/api/erp/masterdata/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.meta.scope").doesNotExist());
    }

    private static Set<String> keys(JsonNode node) {
        Set<String> out = new HashSet<>();
        for (Iterator<String> it = node.fieldNames(); it.hasNext(); ) {
            out.add(it.next());
        }
        return out;
    }
}
