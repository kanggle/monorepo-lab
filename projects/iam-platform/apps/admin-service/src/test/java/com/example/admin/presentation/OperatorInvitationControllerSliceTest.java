package com.example.admin.presentation;

import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.CreateOperatorInvitationCommand;
import com.example.admin.application.OperatorInvitationPageResult;
import com.example.admin.application.OperatorInvitationResult;
import com.example.admin.application.OperatorInvitationUseCase;
import com.example.admin.application.exception.OperatorInvitationAlreadyPendingException;
import com.example.admin.application.exception.OperatorInvitationNotFoundException;
import com.example.admin.application.exception.OperatorInvitationNotPendingException;
import com.example.admin.application.exception.RoleGrantForbiddenException;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.domain.rbac.PermissionEvaluator;
import com.example.admin.presentation.advice.AdminExceptionHandler;
import com.example.admin.presentation.aspect.RequiresPermissionAspect;
import com.example.admin.support.OperatorJwtTestFixture;
import com.example.admin.support.SliceTestSecurityConfig;
import com.example.security.jwt.JwtVerifier;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.aop.AopAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-MONO-772 S2 — the HTTP shape of {@code /api/admin/operator-invitations} (admin-api.md § Operator
 * Invitation): the {@code operator.manage} gate before anything else, the reason / Idempotency-Key headers, the
 * item's always-present keys under the test profile's {@code non_null}, and the error-code mapping of the family.
 */
@WebMvcTest(controllers = OperatorInvitationController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class, RequiresPermissionAspect.class,
        OperatorInvitationControllerSliceTest.JwtBeans.class})
@TestPropertySource(properties = {"admin.jwt.expected-token-type=admin",
        "spring.jackson.default-property-inclusion=non_null"})
class OperatorInvitationControllerSliceTest {

    private static OperatorJwtTestFixture jwt;
    private static final String HOLDER = "op-tenant-admin";
    private static final String NO_PERM = "op-support";
    private static final String INV = "0199de70-0000-7000-8000-00000000inv1";
    private static final String BODY =
            "{\"email\":\"person@example.com\",\"displayName\":\"홍길동\",\"roles\":[\"SUPPORT_LOCK\"],\"tenantId\":\"acme-corp\"}";

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

    @Autowired MockMvc mockMvc;
    @MockitoBean OperatorInvitationUseCase useCase;
    @MockitoBean PermissionEvaluator permissionEvaluator;
    @MockitoBean AdminActionAuditor adminActionAuditor;

    @BeforeEach
    void permissions() {
        lenient().when(permissionEvaluator.hasPermission(HOLDER, Permission.OPERATOR_MANAGE)).thenReturn(true);
        lenient().when(permissionEvaluator.hasPermission(NO_PERM, Permission.OPERATOR_MANAGE)).thenReturn(false);
    }

    private static String bearer(String id) {
        return "Bearer " + jwt.operatorToken(id);
    }

    private static OperatorInvitationResult pending(String auditId) {
        return new OperatorInvitationResult(INV, "acme-corp", "person@example.com", "홍길동", List.of("SUPPORT_LOCK"),
                "PENDING", false, Instant.parse("2026-10-17T10:00:00Z"), Instant.parse("2026-10-10T10:00:00Z"),
                "op-uuid", null, null, null, null, null, auditId);
    }

    @Test
    @DisplayName("🔴 operator.manage 없음 → 403 PERMISSION_DENIED (본문이 틀려도) — 유스케이스 미호출")
    void withoutPermission_403() throws Exception {
        mockMvc.perform(post("/api/admin/operator-invitations")
                        .header("Authorization", bearer(NO_PERM))
                        .header("X-Operator-Reason", "r").header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"broken\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        mockMvc.perform(get("/api/admin/operator-invitations").header("Authorization", bearer(NO_PERM)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/admin/operator-invitations/" + INV + ":cancel")
                        .header("Authorization", bearer(NO_PERM)).header("X-Operator-Reason", "r"))
                .andExpect(status().isForbidden());
        verify(useCase, never()).create(any());
        verify(useCase, never()).list(any(), any(), any(), anyInt(), anyInt());
        verify(useCase, never()).cancel(any(), anyString(), anyString());
    }

    @Test
    @DisplayName("create → 201 · 모든 키 존재(null 포함, non_null 프로필에서도) · auditId · 토큰/해시/링크 키 없음")
    void create_201_shape() throws Exception {
        when(useCase.create(any())).thenReturn(pending("audit-1"));

        String json = mockMvc.perform(post("/api/admin/operator-invitations")
                        .header("Authorization", bearer(HOLDER))
                        .header("X-Operator-Reason", "onboarding").header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invitationId").value(INV))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.expired").value(false))
                .andExpect(jsonPath("$.invitedBy").value("op-uuid"))
                .andExpect(jsonPath("$.auditId").value("audit-1"))
                .andExpect(jsonPath("$.delivery").isEmpty())
                .andExpect(jsonPath("$.acceptedAt").isEmpty())
                .andExpect(jsonPath("$.acceptedOperatorId").isEmpty())
                .andExpect(jsonPath("$.cancelledAt").isEmpty())
                .andReturn().getResponse().getContentAsString();
        assertThat(json).contains("\"delivery\":null").contains("\"acceptedAt\":null");
        assertThat(json.toLowerCase()).doesNotContain("token").doesNotContain("hash").doesNotContain("link");

        ArgumentCaptor<CreateOperatorInvitationCommand> cmd = ArgumentCaptor.forClass(CreateOperatorInvitationCommand.class);
        verify(useCase).create(cmd.capture());
        assertThat(cmd.getValue().idempotencyKey()).isEqualTo("k-1");
        assertThat(cmd.getValue().tenantId()).isEqualTo("acme-corp");
        assertThat(cmd.getValue().actor().operatorId()).isEqualTo(HOLDER);
    }

    @Test
    @DisplayName("사유 헤더 없음 → 400 REASON_REQUIRED (키 없음보다 먼저) · 유스케이스 미호출")
    void create_missingReason() throws Exception {
        mockMvc.perform(post("/api/admin/operator-invitations")
                        .header("Authorization", bearer(HOLDER))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        verify(useCase, never()).create(any());
    }

    @Test
    @DisplayName("오류 매핑: 409 ALREADY_PENDING · 403 ROLE_GRANT_FORBIDDEN · 400 VALIDATION_ERROR")
    void create_errorMapping() throws Exception {
        when(useCase.create(any()))
                .thenThrow(new OperatorInvitationAlreadyPendingException("dup"))
                .thenThrow(new RoleGrantForbiddenException("no"))
                .thenThrow(new IllegalArgumentException("tenantId '*' cannot be invited"));
        String[][] cases = {{"409", "OPERATOR_INVITATION_ALREADY_PENDING"}, {"403", "ROLE_GRANT_FORBIDDEN"},
                {"400", "VALIDATION_ERROR"}};
        for (String[] c : cases) {
            mockMvc.perform(post("/api/admin/operator-invitations")
                            .header("Authorization", bearer(HOLDER))
                            .header("X-Operator-Reason", "r").header("Idempotency-Key", "k")
                            .contentType(MediaType.APPLICATION_JSON).content(BODY))
                    .andExpect(status().is(Integer.parseInt(c[0])))
                    .andExpect(jsonPath("$.code").value(c[1]));
        }
    }

    @Test
    @DisplayName("list → 200 page (query 를 그대로 넘긴다)")
    void list_200() throws Exception {
        when(useCase.list(any(), eq("acme-corp"), eq("PENDING"), eq(0), eq(20)))
                .thenReturn(new OperatorInvitationPageResult(List.of(pending(null)), 1, 0, 20, 1));
        mockMvc.perform(get("/api/admin/operator-invitations?tenantId=acme-corp&status=PENDING")
                        .header("Authorization", bearer(HOLDER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].invitationId").value(INV))
                .andExpect(jsonPath("$.content[0].auditId").doesNotExist())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName(":cancel 404 OPERATOR_INVITATION_NOT_FOUND · :resend 409 OPERATOR_INVITATION_NOT_PENDING · 사유 필수")
    void cancelResend() throws Exception {
        when(useCase.cancel(any(), eq(INV), eq("r"))).thenThrow(new OperatorInvitationNotFoundException("nf"));
        when(useCase.resend(any(), eq(INV), eq("r"))).thenThrow(new OperatorInvitationNotPendingException("np"));

        mockMvc.perform(post("/api/admin/operator-invitations/" + INV + ":cancel")
                        .header("Authorization", bearer(HOLDER)).header("X-Operator-Reason", "r"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("OPERATOR_INVITATION_NOT_FOUND"));
        mockMvc.perform(post("/api/admin/operator-invitations/" + INV + ":resend")
                        .header("Authorization", bearer(HOLDER)).header("X-Operator-Reason", "r"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPERATOR_INVITATION_NOT_PENDING"));
        mockMvc.perform(post("/api/admin/operator-invitations/" + INV + ":resend")
                        .header("Authorization", bearer(HOLDER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
    }
}
