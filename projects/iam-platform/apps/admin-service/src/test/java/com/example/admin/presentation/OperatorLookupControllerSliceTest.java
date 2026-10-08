package com.example.admin.presentation;

import com.example.admin.application.ActionCode;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.OperatorEmailLookupUseCase;
import com.example.admin.application.QueryTenantScopeGate;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.domain.rbac.PermissionEvaluator;
import com.example.admin.presentation.advice.AdminExceptionHandler;
import com.example.admin.presentation.aspect.RequiresPermissionAspect;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.admin.support.SliceTestSecurityConfig;
import com.example.security.jwt.JwtVerifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-777 — {@code GET /api/admin/operators/lookup} (admin-api.md § same name).
 *
 * <p>The REAL {@link OperatorEmailLookupUseCase} runs; only its two collaborators are mocked
 * (the shared tenant gate and the operator port), so the «out of scope = not found» rule is
 * measured through the actual catch, not a stubbed use case. The RBAC aspect is imported so the
 * «no permission key» property is measured with the deny-default machinery present.
 */
@WebMvcTest(controllers = OperatorLookupController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class, OperatorEmailLookupUseCase.class,
        OperatorLookupControllerSliceTest.JwtBeans.class})
@TestPropertySource(properties = {
        "admin.jwt.expected-token-type=admin"
})
class OperatorLookupControllerSliceTest {

    private static OperatorJwtTestFixture jwt;

    @BeforeAll
    static void initFixture() {
        jwt = new OperatorJwtTestFixture();
    }

    @org.springframework.boot.test.context.TestConfiguration
    static class JwtBeans {
        @Bean
        JwtVerifier operatorJwtVerifier() {
            if (jwt == null) jwt = new OperatorJwtTestFixture();
            return jwt.verifier();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    QueryTenantScopeGate queryTenantScopeGate;

    @MockitoBean
    AdminOperatorPort operatorPort;

    @MockitoBean
    PermissionEvaluator permissionEvaluator;

    @MockitoBean
    AdminActionAuditor auditor;

    private static final String NOT_FOUND_EMAIL = "nobody@demo-corp.example";

    @BeforeEach
    void zeroPermissions() {
        // Every case runs as an operator holding NO admin permission (e.g. an erp HR operator):
        // the endpoint must not need one.
        when(permissionEvaluator.hasPermission(anyString(), anyString())).thenReturn(false);
        when(permissionEvaluator.hasAllPermissions(anyString(), any(Collection.class))).thenReturn(false);
        when(queryTenantScopeGate.resolve(any(), eq("demo-corp"), any(), anyString()))
                .thenReturn(new QueryTenantScopeGate.Resolved("demo-corp", false));
        when(operatorPort.findLookupCandidates(eq("demo-corp"), eq("demo@demo.com")))
                .thenReturn(List.of(new AdminOperatorPort.OperatorLookupView(
                        "0199de70-0000-7000-8000-00000000ad03", "Demo Operator", "demo-corp")));
        when(operatorPort.findLookupCandidates(eq("demo-corp"), eq(NOT_FOUND_EMAIL)))
                .thenReturn(List.of());
    }

    private String bearer() {
        return "Bearer " + jwt.operatorToken("op-hr");
    }

    @Test
    void found_returns_the_operators_oidc_subject_as_accountId() throws Exception {
        mockMvc.perform(get("/api/admin/operators/lookup")
                        .param("email", "  Demo@Demo.com ")   // normalized: trim + lower-case
                        .param("tenantId", "demo-corp")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].accountId").value("0199de70-0000-7000-8000-00000000ad03"))
                .andExpect(jsonPath("$.content[0].displayName").value("Demo Operator"))
                .andExpect(jsonPath("$.content[0].tenantId").value("demo-corp"))
                // only what the link needs — no operatorId / email / roles on the wire
                .andExpect(jsonPath("$.content[0].operatorId").doesNotExist())
                .andExpect(jsonPath("$.content[0].email").doesNotExist());
    }

    @Test
    void not_found_is_200_with_an_empty_list() throws Exception {
        String body = mockMvc.perform(get("/api/admin/operators/lookup")
                        .param("email", NOT_FOUND_EMAIL)
                        .param("tenantId", "demo-corp")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).isEqualTo("{\"content\":[]}");
    }

    /**
     * 🔴 Existence non-disclosure: out of scope answers byte-for-byte like «not found» — not
     * the 403 TENANT_SCOPE_DENIED that GET /api/admin/accounts gives.
     */
    @Test
    void out_of_scope_tenant_is_byte_identical_to_not_found() throws Exception {
        when(queryTenantScopeGate.resolve(any(), eq("other-corp"), any(), anyString()))
                .thenThrow(new TenantScopeDeniedException("out of scope"));

        var notFound = mockMvc.perform(get("/api/admin/operators/lookup")
                        .param("email", NOT_FOUND_EMAIL)
                        .param("tenantId", "demo-corp")
                        .header("Authorization", bearer()))
                .andReturn().getResponse();
        var outOfScope = mockMvc.perform(get("/api/admin/operators/lookup")
                        .param("email", "demo@demo.com")
                        .param("tenantId", "other-corp")
                        .header("Authorization", bearer()))
                .andReturn().getResponse();

        assertThat(outOfScope.getStatus()).isEqualTo(notFound.getStatus()).isEqualTo(200);
        assertThat(outOfScope.getContentAsString()).isEqualTo(notFound.getContentAsString());
        assertThat(outOfScope.getContentType()).isEqualTo(notFound.getContentType());
        // the gate ran (and owns the best-effort DENIED row); no operator row was read
        verify(queryTenantScopeGate).resolve(any(), eq("other-corp"), eq(ActionCode.ACCOUNT_SEARCH), anyString());
        verify(operatorPort, never()).findLookupCandidates(eq("other-corp"), anyString());
    }

    @Test
    void zero_permission_operator_gets_200_and_no_denied_row() throws Exception {
        mockMvc.perform(get("/api/admin/operators/lookup")
                        .param("email", "demo@demo.com")
                        .param("tenantId", "demo-corp")
                        .header("Authorization", bearer()))
                .andExpect(status().isOk());
        verify(auditor, never()).recordDenied(any(), anyString(), any(), any(), any());
    }

    @Test
    void blank_email_is_400_validation_error() throws Exception {
        mockMvc.perform(get("/api/admin/operators/lookup")
                        .param("email", "   ")
                        .param("tenantId", "demo-corp")
                        .header("Authorization", bearer()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void without_operator_jwt_is_401() throws Exception {
        mockMvc.perform(get("/api/admin/operators/lookup").param("email", "demo@demo.com"))
                .andExpect(status().isUnauthorized());
    }
}
