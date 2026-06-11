package com.example.admin.presentation.aspect;

import com.example.admin.application.AccountAdminUseCase;
import com.example.admin.application.ActionCode;
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
import com.example.security.access.TimeWindowCondition;
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

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * ADR-MONO-028 — verifies {@link RequiresPermissionAspect}'s 4th authorization gate
 * for the {@code TIME_WINDOW} access condition. With a configured window
 * (MON–SUN 09:00–18:00 UTC) an RBAC-granted admin mutation is allowed only when the
 * request time (the injected {@link Clock}) falls within the window, and otherwise
 * 403 {@code ACCESS_CONDITION_UNMET}. (The net-zero "no bean configured" path is
 * covered by {@link RequiresPermissionAspectTest}, which provides no condition bean
 * and still proceeds; the AND-only composition with {@code SOURCE_IP} is covered by
 * {@link AdminAccessConditionCompositionTest}.)
 */
@WebMvcTest(controllers = AccountAdminController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        AdminTimeWindowConditionEnforcementTest.Beans.class})
class AdminTimeWindowConditionEnforcementTest {

    /** Wed 2026-06-10 12:00 UTC — inside the 09:00–18:00 window. */
    private static final Instant IN_WINDOW = Instant.parse("2026-06-10T12:00:00Z");
    /** Wed 2026-06-10 20:00 UTC — after 18:00, outside the window. */
    private static final Instant OUT_OF_WINDOW = Instant.parse("2026-06-10T20:00:00Z");

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

        /** Configured window (all days, 09:00–18:00 UTC) → the TIME_WINDOW gate is active. */
        @Bean
        TimeWindowCondition timeWindowCondition() {
            return TimeWindowCondition.fromConfig(
                    "UTC",
                    List.of("MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"),
                    "09:00", "18:00");
        }
    }

    @Autowired MockMvc mockMvc;
    @MockitoBean AccountAdminUseCase useCase;
    @MockitoBean BulkLockAccountUseCase bulkLockUseCase;
    @MockitoBean AccountServiceClient accountServiceClient;
    @MockitoBean PermissionEvaluator permissionEvaluator;
    @MockitoBean AdminActionAuditor auditor;
    /** The request clock the aspect resolves for TIME_WINDOW — stubbed per test. */
    @MockitoBean Clock clock;

    private String tokenFor(String operatorId) {
        return "Bearer " + jwt.operatorToken(operatorId);
    }

    private void grantPermission(String operatorId) {
        when(permissionEvaluator.hasPermission(operatorId, Permission.ACCOUNT_LOCK)).thenReturn(true);
        when(useCase.lock(any())).thenReturn(new LockAccountResult(
                "acc-1", "ACTIVE", "LOCKED", operatorId, Instant.now(), "audit-ok"));
    }

    @Test
    @DisplayName("met: RBAC granted + in-window request time ⟹ 200 (condition satisfied)")
    void allowsWhenInWindow() throws Exception {
        grantPermission("op-super");
        when(clock.instant()).thenReturn(IN_WINDOW);

        mockMvc.perform(post("/api/admin/accounts/acc-1/lock")
                        .header("Authorization", tokenFor("op-super"))
                        .header("Idempotency-Key", "idemp-in")
                        .header("X-Operator-Reason", "compliance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());

        verify(auditor, never()).recordDenied(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("unmet: RBAC granted + out-of-window request time ⟹ 403 ACCESS_CONDITION_UNMET, mutation not executed")
    void deniesWhenOutOfWindow() throws Exception {
        grantPermission("op-super");
        when(clock.instant()).thenReturn(OUT_OF_WINDOW);

        mockMvc.perform(post("/api/admin/accounts/acc-1/lock")
                        .header("Authorization", tokenFor("op-super"))
                        .header("Idempotency-Key", "idemp-out")
                        .header("X-Operator-Reason", "compliance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_CONDITION_UNMET"));

        verify(useCase, never()).lock(any());
        verify(auditor).recordDenied(
                org.mockito.ArgumentMatchers.eq(ActionCode.ACCOUNT_LOCK),
                org.mockito.ArgumentMatchers.eq(Permission.ACCOUNT_LOCK),
                org.mockito.ArgumentMatchers.eq("/api/admin/accounts/acc-1/lock"),
                org.mockito.ArgumentMatchers.eq("POST"),
                any());
    }

    @Test
    @DisplayName("ordering: RBAC denial wins over the time-window gate (PERMISSION_DENIED, not ACCESS_CONDITION_UNMET)")
    void permissionDenialTakesPrecedence() throws Exception {
        when(permissionEvaluator.hasPermission("op-readonly", Permission.ACCOUNT_LOCK)).thenReturn(false);

        mockMvc.perform(post("/api/admin/accounts/acc-1/lock")
                        .header("Authorization", tokenFor("op-readonly"))
                        .header("Idempotency-Key", "idemp-perm")
                        .header("X-Operator-Reason", "compliance")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
    }
}
