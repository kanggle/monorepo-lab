package com.example.admin.presentation.aspect;

import com.example.admin.application.AccountAdminUseCase;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.BulkLockAccountUseCase;
import com.example.admin.application.LockAccountResult;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.domain.rbac.PermissionEvaluator;
import com.example.admin.infrastructure.client.AccountServiceClient;
import com.example.admin.presentation.AccountAdminController;
import com.example.admin.presentation.advice.AdminExceptionHandler;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.admin.support.SliceTestSecurityConfig;
import com.example.security.access.ResourceTagCondition;
import com.example.security.jwt.JwtVerifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-354 — verifies {@link RequiresPermissionAspect}'s RESOURCE_TAG gate with
 * <b>both</b> modes configured: a {@code forbidden} (deny-if-present) condition AND a
 * {@code required} (deny-if-absent) condition, composed <b>AND-only</b>. This proves
 * (a) the new require mode denies a target that lacks a required tag, and (b) the
 * aspect's generalisation to multiple {@link ResourceTagCondition} beans
 * (consumed via {@code ObjectProvider.orderedStream()}) composes them correctly —
 * any one unsatisfied condition denies.
 *
 * <p>Sibling to {@code AdminResourceTagConditionEnforcementTest} (single forbidden
 * bean, the BE-353 net-zero baseline). The resolver is mocked (the aspect-composition
 * layer); path-matching + the DB projection are covered by
 * {@code OperatorResourceTagResolverTest}.
 */
@WebMvcTest(controllers = AccountAdminController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@org.springframework.context.annotation.Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        AdminRequireTagConditionEnforcementTest.Beans.class})
class AdminRequireTagConditionEnforcementTest {

    private static OperatorJwtTestFixture jwt;

    @BeforeAll
    static void init() {
        jwt = new OperatorJwtTestFixture();
    }

    @TestConfiguration
    static class Beans {
        @Bean
        JwtVerifier operatorJwtVerifier() {
            if (jwt == null) jwt = new OperatorJwtTestFixture();
            return jwt.verifier();
        }

        /** deny-if-present: `protected` blocks the mutation. */
        @Bean
        ResourceTagCondition resourceTagCondition() {
            return ResourceTagCondition.forbidden(List.of("protected"));
        }

        /** require: the target must carry `certified` (deny-if-absent). */
        @Bean
        ResourceTagCondition requiredResourceTagCondition() {
            return ResourceTagCondition.required(List.of("certified"));
        }
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean AccountAdminUseCase useCase;
    @MockitoBean BulkLockAccountUseCase bulkLockUseCase;
    @MockitoBean AccountServiceClient accountServiceClient;
    @MockitoBean PermissionEvaluator permissionEvaluator;
    @MockitoBean AdminActionAuditor auditor;
    // TASK-BE-357: AccountAdminController now depends on the shared read-tenant gate.
    @MockitoBean com.example.admin.application.QueryTenantScopeGate queryTenantScopeGate;
    @MockitoBean ResourceTagResolver resourceTagResolver;

    private String tokenFor(String operatorId) {
        return "Bearer " + jwt.operatorToken(operatorId);
    }

    private void grantPermission() {
        when(permissionEvaluator.hasPermission("op-super", Permission.ACCOUNT_LOCK)).thenReturn(true);
        when(useCase.lock(any())).thenReturn(new LockAccountResult(
                "acc-1", "ACTIVE", "LOCKED", "op-super", Instant.now(), "audit-ok"));
        // TASK-BE-467: allowed-path reaches the controller body, which now consults
        // the shared read-tenant gate before invoking the use case.
        when(queryTenantScopeGate.resolve(any(), any(), any(), any()))
                .thenReturn(new com.example.admin.application.QueryTenantScopeGate.Resolved("fan-platform", true));
    }

    private org.springframework.test.web.servlet.ResultActions lock(String idemp) throws Exception {
        return mockMvc.perform(post("/api/admin/accounts/acc-1/lock")
                .header("Authorization", tokenFor("op-super"))
                .header("Idempotency-Key", idemp)
                .header("X-Operator-Reason", "compliance")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    @Test
    @DisplayName("AND: target {certified} ⟹ 200 (forbidden ok — no `protected`; require ok — has `certified`)")
    void allowsWhenRequiredPresentAndForbiddenAbsent() throws Exception {
        grantPermission();
        when(resourceTagResolver.resolveResourceTags(any())).thenReturn(Optional.of(Set.of("certified")));

        lock("idemp-both-ok").andExpect(status().isOk());

        verify(auditor, never()).recordDenied(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("require deny-if-absent: target lacks `certified` (empty) ⟹ 403 ACCESS_CONDITION_UNMET, not executed")
    void deniesWhenRequiredTagAbsent() throws Exception {
        grantPermission();
        when(resourceTagResolver.resolveResourceTags(any())).thenReturn(Optional.of(Set.of()));

        lock("idemp-untagged")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_CONDITION_UNMET"));

        verify(useCase, never()).lock(any());
    }

    @Test
    @DisplayName("require deny-if-absent: target has other tags but not `certified` ⟹ 403")
    void deniesWhenRequiredTagMissingAmongOthers() throws Exception {
        grantPermission();
        when(resourceTagResolver.resolveResourceTags(any())).thenReturn(Optional.of(Set.of("staging")));

        lock("idemp-other").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_CONDITION_UNMET"));

        verify(useCase, never()).lock(any());
    }

    @Test
    @DisplayName("AND: forbidden wins — target {certified, protected} ⟹ 403 (require ok but forbidden present)")
    void deniesWhenForbiddenPresentEvenIfRequiredSatisfied() throws Exception {
        grantPermission();
        when(resourceTagResolver.resolveResourceTags(any()))
                .thenReturn(Optional.of(Set.of("certified", "protected")));

        lock("idemp-both-conflict").andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_CONDITION_UNMET"));

        verify(useCase, never()).lock(any());
    }

    @Test
    @DisplayName("net-zero (not applicable): request targets no resolvable resource ⟹ 200 (both modes skipped)")
    void allowsWhenNotApplicable() throws Exception {
        grantPermission();
        when(resourceTagResolver.resolveResourceTags(any())).thenReturn(Optional.empty());

        lock("idemp-na").andExpect(status().isOk());
    }
}
