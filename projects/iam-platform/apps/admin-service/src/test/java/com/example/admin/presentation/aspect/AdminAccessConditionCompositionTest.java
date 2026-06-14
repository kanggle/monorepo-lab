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
import com.example.security.access.SourceIpCondition;
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
 * ADR-MONO-028 § D2 — verifies the <b>AND-only composition</b> of two access
 * conditions on the SAME admin mutation surface: {@code SOURCE_IP} (allowlist
 * {@code 10.0.0.0/8}) <b>AND</b> {@code TIME_WINDOW} (MON–SUN 09:00–18:00 UTC).
 * An RBAC-granted mutation proceeds only when BOTH hold; any unsatisfied condition
 * → 403 {@code ACCESS_CONDITION_UNMET}. This is the blessed-but-unproven
 * multi-condition composition (ADR-026 § D1) that the {@code SOURCE_IP} pilot
 * (BE-351) — a single condition — did not exercise.
 *
 * <p>Truth table proven below (the source IP comes from {@code X-Forwarded-For};
 * the time from the injected {@link Clock}):
 * <pre>
 *   in-CIDR  + in-window  → 200   (both satisfied)
 *   in-CIDR  + out-window → 403   (TIME_WINDOW gates despite SOURCE_IP passing)
 *   out-CIDR + (any)      → 403   (SOURCE_IP gates first; AND short-circuits)
 * </pre>
 */
@WebMvcTest(controllers = AccountAdminController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class,
        RequiresPermissionAspect.class,
        AdminAccessConditionCompositionTest.Beans.class})
class AdminAccessConditionCompositionTest {

    private static final String IN_CIDR = "10.1.2.3";       // ∈ 10.0.0.0/8
    private static final String OUT_OF_CIDR = "203.0.113.7"; // TEST-NET-3, outside
    private static final Instant IN_WINDOW = Instant.parse("2026-06-10T12:00:00Z");  // Wed 12:00
    private static final Instant OUT_OF_WINDOW = Instant.parse("2026-06-10T20:00:00Z"); // Wed 20:00

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

        @Bean
        SourceIpCondition sourceIpCondition() {
            return SourceIpCondition.fromAllowedCidrs(List.of("10.0.0.0/8"));
        }

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
    // TASK-BE-357: AccountAdminController now depends on the shared read-tenant gate.
    @MockitoBean com.example.admin.application.QueryTenantScopeGate queryTenantScopeGate;
    @MockitoBean Clock clock;

    private String tokenFor(String operatorId) {
        return "Bearer " + jwt.operatorToken(operatorId);
    }

    private void grantPermission() {
        when(permissionEvaluator.hasPermission("op-super", Permission.ACCOUNT_LOCK)).thenReturn(true);
        when(useCase.lock(any())).thenReturn(new LockAccountResult(
                "acc-1", "ACTIVE", "LOCKED", "op-super", Instant.now(), "audit-ok"));
    }

    private org.springframework.test.web.servlet.ResultActions lock(String xff) throws Exception {
        return mockMvc.perform(post("/api/admin/accounts/acc-1/lock")
                .header("Authorization", tokenFor("op-super"))
                .header("Idempotency-Key", "idemp-" + xff)
                .header("X-Operator-Reason", "compliance")
                .header("X-Forwarded-For", xff)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));
    }

    @Test
    @DisplayName("in-CIDR AND in-window ⟹ 200 (both conditions satisfied)")
    void allowsWhenBothSatisfied() throws Exception {
        grantPermission();
        when(clock.instant()).thenReturn(IN_WINDOW);

        lock(IN_CIDR).andExpect(status().isOk());

        verify(auditor, never()).recordDenied(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("in-CIDR AND out-of-window ⟹ 403 (TIME_WINDOW gates despite SOURCE_IP passing)")
    void deniesWhenTimeUnmetEvenIfIpOk() throws Exception {
        grantPermission();
        when(clock.instant()).thenReturn(OUT_OF_WINDOW);

        lock(IN_CIDR)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_CONDITION_UNMET"));

        verify(useCase, never()).lock(any());
    }

    @Test
    @DisplayName("out-of-CIDR ⟹ 403 (SOURCE_IP gates first; the AND short-circuits before the time check)")
    void deniesWhenIpUnmet() throws Exception {
        grantPermission();
        // No clock stub: SOURCE_IP is evaluated first and short-circuits the AND,
        // so the TIME_WINDOW check (clock.instant()) is never reached.

        lock(OUT_OF_CIDR)
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_CONDITION_UNMET"));

        verify(useCase, never()).lock(any());
    }
}
