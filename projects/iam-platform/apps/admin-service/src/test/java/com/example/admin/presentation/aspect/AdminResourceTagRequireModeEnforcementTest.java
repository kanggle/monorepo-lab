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
import org.springframework.test.web.servlet.ResultActions;

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
 * TASK-BE-354 — verifies {@link RequiresPermissionAspect}'s 4th gate for the
 * {@code RESOURCE_TAG} access condition's <b>require</b> mode (deny-if-absent), and
 * its <b>AND composition</b> with the existing <b>forbidden</b> mode (deny-if-present).
 *
 * <p>Both modes are configured ({@code forbidden=[protected]} AND
 * {@code required=[certified]}) via two {@link ResourceTagCondition} beans, consumed
 * by the aspect's {@code ObjectProvider.orderedStream()} (no by-name / single-type
 * injection). The once-resolved tag set is evaluated against every configured
 * condition; any unsatisfied condition denies (AND-only). The truth table:
 * <ul>
 *   <li>{@code {certified}} → allowed (require satisfied, no forbidden tag)</li>
 *   <li>{@code {certified, protected}} → denied (forbidden fails)</li>
 *   <li>{@code {}} (untagged) → denied (require fails — deny-if-absent)</li>
 *   <li>resolver {@code Optional.empty()} → allowed (no resolvable resource → skipped)</li>
 * </ul>
 *
 * <p>The forbidden-only path (and AC-3 net-zero with a single bean) stays covered by
 * {@code AdminResourceTagConditionEnforcementTest}; the real path-matching + DB
 * resolver by {@code OperatorResourceTagResolverTest}.
 */
@WebMvcTest(controllers = AccountAdminController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        AdminResourceTagRequireModeEnforcementTest.Beans.class})
class AdminResourceTagRequireModeEnforcementTest {

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

        /** deny-if-present mode — both gates active so the aspect composes them AND-only. */
        @Bean
        ResourceTagCondition resourceTagCondition() {
            return ResourceTagCondition.forbidden(List.of("protected"));
        }

        /** require mode (deny-if-absent) — the new gate this task wires. */
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

    private ResultActions lock(String idemp) throws Exception {
        return mockMvc.perform(post("/api/admin/accounts/acc-1/lock")
                .header("Authorization", tokenFor("op-super"))
                .header("Idempotency-Key", idemp)
                .header("X-Operator-Reason", "compliance")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    @Test
    @DisplayName("require satisfied: target carries `certified` (no forbidden tag) ⟹ 200")
    void allowsWhenRequiredTagPresent() throws Exception {
        grantPermission();
        when(resourceTagResolver.resolveResourceTags(any())).thenReturn(Optional.of(Set.of("certified")));

        lock("idemp-certified").andExpect(status().isOk());

        verify(auditor, never()).recordDenied(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("AND composition: target carries `certified` AND forbidden `protected` ⟹ 403 (forbidden fails)")
    void deniesWhenForbiddenTagAlsoPresent() throws Exception {
        grantPermission();
        when(resourceTagResolver.resolveResourceTags(any()))
                .thenReturn(Optional.of(Set.of("certified", "protected")));

        lock("idemp-both")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_CONDITION_UNMET"));

        verify(useCase, never()).lock(any());
    }

    @Test
    @DisplayName("require fails (deny-if-absent): untagged target (empty set) ⟹ 403 ACCESS_CONDITION_UNMET")
    void deniesWhenRequiredTagAbsent() throws Exception {
        grantPermission();
        when(resourceTagResolver.resolveResourceTags(any())).thenReturn(Optional.of(Set.of()));

        lock("idemp-untagged")
                .andExpect(status().isForbidden())
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
