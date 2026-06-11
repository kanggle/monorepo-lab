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
import org.springframework.context.annotation.Import;
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
 * ADR-MONO-029 — verifies {@link RequiresPermissionAspect}'s 4th authorization gate
 * for the {@code RESOURCE_TAG} access condition (deny-if-present on {@code protected}).
 * With a configured forbidden-tag set, an RBAC-granted admin mutation is denied when
 * the {@link ResourceTagResolver} reports the target resource carries the tag, and
 * allowed otherwise; a request that targets no resolvable resource is skipped
 * (net-zero). The resolver is mocked here (the aspect-composition layer); the real
 * path-matching + DB resolver is covered by {@code OperatorResourceTagResolverTest}
 * and the federation-e2e proof.
 */
@WebMvcTest(controllers = AccountAdminController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        AdminResourceTagConditionEnforcementTest.Beans.class})
class AdminResourceTagConditionEnforcementTest {

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

        /** Configured forbidden tag (`protected`) → the RESOURCE_TAG gate is active. */
        @Bean
        ResourceTagCondition resourceTagCondition() {
            return ResourceTagCondition.forbidden(List.of("protected"));
        }
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean AccountAdminUseCase useCase;
    @MockitoBean BulkLockAccountUseCase bulkLockUseCase;
    @MockitoBean AccountServiceClient accountServiceClient;
    @MockitoBean PermissionEvaluator permissionEvaluator;
    @MockitoBean AdminActionAuditor auditor;
    /** The seam resolver — stubbed per test to report the target's tags. */
    @MockitoBean ResourceTagResolver resourceTagResolver;

    private String tokenFor(String operatorId) {
        return "Bearer " + jwt.operatorToken(operatorId);
    }

    private void grantPermission() {
        when(permissionEvaluator.hasPermission("op-super", Permission.ACCOUNT_LOCK)).thenReturn(true);
        when(useCase.lock(any())).thenReturn(new LockAccountResult(
                "acc-1", "ACTIVE", "LOCKED", "op-super", Instant.now(), "audit-ok"));
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
    @DisplayName("deny-if-present: target resource carries `protected` ⟹ 403 ACCESS_CONDITION_UNMET, mutation not executed")
    void deniesWhenResourceTagged() throws Exception {
        grantPermission();
        when(resourceTagResolver.resolveResourceTags(any())).thenReturn(Optional.of(Set.of("protected")));

        lock("idemp-tagged")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_CONDITION_UNMET"));

        verify(useCase, never()).lock(any());
    }

    @Test
    @DisplayName("allowed: target resource untagged (empty set) ⟹ 200 (no forbidden tag present)")
    void allowsWhenResourceUntagged() throws Exception {
        grantPermission();
        when(resourceTagResolver.resolveResourceTags(any())).thenReturn(Optional.of(Set.of()));

        lock("idemp-untagged").andExpect(status().isOk());

        verify(auditor, never()).recordDenied(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("net-zero (not applicable): request targets no resolvable resource ⟹ 200 (RESOURCE_TAG skipped)")
    void allowsWhenNotApplicable() throws Exception {
        grantPermission();
        when(resourceTagResolver.resolveResourceTags(any())).thenReturn(Optional.empty());

        lock("idemp-na").andExpect(status().isOk());
    }
}
