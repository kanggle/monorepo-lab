package com.example.admin.presentation.aspect;

import com.example.admin.application.AccountAdminUseCase;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.BulkLockAccountUseCase;
import com.example.admin.application.LockAccountResult;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.domain.rbac.PermissionEvaluator;
import com.example.admin.infrastructure.access.AccountResourceTagResolver;
import com.example.admin.infrastructure.client.AccountServiceClient;
import com.example.admin.infrastructure.persistence.access.AdminResourceTagJpaRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-355 — verifies {@link RequiresPermissionAspect}'s RESOURCE_TAG gate now
 * consults <b>multiple</b> {@link ResourceTagResolver} beans (generalised from a
 * single resolver to {@code ObjectProvider.orderedStream()}). The real
 * {@link AccountResourceTagResolver} (reading the admin-local table) gates an account
 * lock by the target's tags, while a second resolver that does not match the account
 * path returns empty (skipped) — proving the aspect iterates resolvers and the one
 * applicable resolver decides. Sibling to the operator (BE-353) and require-mode
 * (BE-354) enforcement tests.
 */
@WebMvcTest(controllers = AccountAdminController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        MultiResolverTagConditionEnforcementTest.Beans.class})
class MultiResolverTagConditionEnforcementTest {

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

        /** Configured forbidden tag → the RESOURCE_TAG gate is active. */
        @Bean
        ResourceTagCondition resourceTagCondition() {
            return ResourceTagCondition.forbidden(List.of("protected"));
        }

        /** The real account resolver (admin-local table lookup), under test. */
        @Bean
        AccountResourceTagResolver accountResourceTagResolver(AdminResourceTagJpaRepository repo) {
            return new AccountResourceTagResolver(repo);
        }

        /** A second resolver that never matches the account path → always skipped
         * (stands in for the operator/tenant resolvers; proves multi-resolver iteration). */
        @Bean
        ResourceTagResolver alwaysEmptyResolver() {
            return request -> Optional.empty();
        }
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean AccountAdminUseCase useCase;
    @MockitoBean BulkLockAccountUseCase bulkLockUseCase;
    @MockitoBean AccountServiceClient accountServiceClient;
    @MockitoBean PermissionEvaluator permissionEvaluator;
    @MockitoBean AdminActionAuditor auditor;
    /** The trusted admin-local tag source the account resolver reads. */
    @MockitoBean AdminResourceTagJpaRepository resourceTagRepository;

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
    @DisplayName("account tagged `protected` (admin-local table) ⟹ 403, resolved by the applicable resolver among many")
    void deniesWhenAccountTagged() throws Exception {
        grantPermission();
        when(resourceTagRepository.findTags("ACCOUNT", "acc-1")).thenReturn(Optional.of("protected"));

        lock("idemp-acct-tagged")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_CONDITION_UNMET"));

        verify(useCase, never()).lock(any());
    }

    @Test
    @DisplayName("account untagged (absent row) ⟹ 200 (no forbidden tag; other resolvers skipped)")
    void allowsWhenAccountUntagged() throws Exception {
        grantPermission();
        when(resourceTagRepository.findTags("ACCOUNT", "acc-1")).thenReturn(Optional.empty());

        lock("idemp-acct-untagged").andExpect(status().isOk());

        verify(auditor, never()).recordDenied(any(), any(), any(), any(), any());
    }
}
