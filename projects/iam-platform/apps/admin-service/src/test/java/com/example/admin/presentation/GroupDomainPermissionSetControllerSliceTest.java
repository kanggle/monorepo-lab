package com.example.admin.presentation;

import com.example.admin.application.ActionCode;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.GrantGroupDomainPermissionSetCommand;
import com.example.admin.application.GroupDomainPermissionSetResult;
import com.example.admin.application.GroupDomainPermissionSetUseCase;
import com.example.admin.application.exception.DomainNotSubscribedException;
import com.example.admin.application.exception.DomainPermissionSetAlreadyGrantedException;
import com.example.admin.application.exception.DomainPermissionSetNotFoundException;
import com.example.admin.application.exception.GroupNotFoundException;
import com.example.admin.application.exception.IdempotencyKeyConflictException;
import com.example.admin.application.exception.TenantScopeDeniedException;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-631 slice B — the HTTP shape of {@code GET|POST /api/admin/groups/{groupId}/domain-permission-sets} ·
 * {@code DELETE …/{setId}}: the {@code domain_access.manage} gate with its DENIED row (action code
 * {@code GROUP_DOMAIN_SET_GRANT} / {@code _REVOKE}), the wire shapes, the headers reaching the command, and the error
 * codes of the contract's table.
 */
@WebMvcTest(controllers = GroupDomainPermissionSetController.class)
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class, RequiresPermissionAspect.class,
        GroupDomainPermissionSetControllerSliceTest.JwtBeans.class})
@TestPropertySource(properties = {"admin.jwt.expected-token-type=admin",
        "spring.jackson.default-property-inclusion=non_null"})
class GroupDomainPermissionSetControllerSliceTest {

    private static OperatorJwtTestFixture jwt;
    private static final String HOLDER = "op-tenant-admin";
    private static final String NO_KEY = "op-org-admin";

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

    @MockitoBean GroupDomainPermissionSetUseCase useCase;
    @MockitoBean PermissionEvaluator permissionEvaluator;
    @MockitoBean AdminActionAuditor adminActionAuditor;

    @BeforeEach
    void permissions() {
        lenient().when(permissionEvaluator.hasPermission(HOLDER, Permission.DOMAIN_ACCESS_MANAGE)).thenReturn(true);
        lenient().when(permissionEvaluator.hasPermission(NO_KEY, Permission.DOMAIN_ACCESS_MANAGE)).thenReturn(false);
    }

    private static String bearer(String operatorId) {
        return "Bearer " + jwt.operatorToken(operatorId);
    }

    @Test
    @DisplayName("no domain_access.manage → 403 PERMISSION_DENIED + DENIED row with GROUP_DOMAIN_SET_GRANT / _REVOKE; use case untouched")
    void withoutPermission_403_deniedRow() throws Exception {
        mockMvc.perform(post("/api/admin/groups/grp-1/domain-permission-sets")
                        .header("Authorization", bearer(NO_KEY))
                        .header("X-Operator-Reason", "r").header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"setId\":\"s1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        verify(adminActionAuditor).recordDenied(eq(ActionCode.GROUP_DOMAIN_SET_GRANT),
                eq(Permission.DOMAIN_ACCESS_MANAGE), anyString(), eq("POST"), isNull());

        mockMvc.perform(delete("/api/admin/groups/grp-1/domain-permission-sets/s1")
                        .header("Authorization", bearer(NO_KEY)).header("X-Operator-Reason", "r"))
                .andExpect(status().isForbidden());
        verify(adminActionAuditor).recordDenied(eq(ActionCode.GROUP_DOMAIN_SET_REVOKE),
                eq(Permission.DOMAIN_ACCESS_MANAGE), anyString(), eq("DELETE"), isNull());

        mockMvc.perform(get("/api/admin/groups/grp-1/domain-permission-sets").header("Authorization", bearer(NO_KEY)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(useCase);
    }

    @Test
    @DisplayName("GET → {items:[{setId, name, systemKey(null kept), grantedAt, grantedBy}]}")
    void list_wireShape() throws Exception {
        when(useCase.list(any(), eq("grp-1"))).thenReturn(List.of(new GroupDomainPermissionSetResult("s1", "재고팀",
                null, Instant.parse("2026-10-14T09:00:00Z"), "op-x")));
        mockMvc.perform(get("/api/admin/groups/grp-1/domain-permission-sets").header("Authorization", bearer(HOLDER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].setId").value("s1"))
                .andExpect(jsonPath("$.items[0].name").value("재고팀"))
                .andExpect(jsonPath("$.items[0].systemKey").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].grantedAt").value("2026-10-14T09:00:00Z"))
                .andExpect(jsonPath("$.items[0].grantedBy").value("op-x"));
    }

    @Test
    @DisplayName("POST → 201 {item, fannedOutRows}; reason percent-decoded, key and setId reach the command")
    void grant_201() throws Exception {
        when(useCase.grant(any())).thenReturn(new GroupDomainPermissionSetResult.Granted(
                new GroupDomainPermissionSetResult("s1", "재고팀", null, Instant.parse("2026-10-14T09:00:00Z"), HOLDER),
                5));
        mockMvc.perform(post("/api/admin/groups/grp-1/domain-permission-sets")
                        .header("Authorization", bearer(HOLDER))
                        .header("X-Operator-Reason", "%EC%9E%AC%EA%B3%A0")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"setId\":\"s1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.item.setId").value("s1"))
                .andExpect(jsonPath("$.item.grantedBy").value(HOLDER))
                .andExpect(jsonPath("$.fannedOutRows").value(5));
        ArgumentCaptor<GrantGroupDomainPermissionSetCommand> cmd =
                ArgumentCaptor.forClass(GrantGroupDomainPermissionSetCommand.class);
        verify(useCase).grant(cmd.capture());
        assertThat(cmd.getValue().groupId()).isEqualTo("grp-1");
        assertThat(cmd.getValue().setId()).isEqualTo("s1");
        assertThat(cmd.getValue().reason()).isEqualTo("재고");
        assertThat(cmd.getValue().idempotencyKey()).isEqualTo("k-1");
    }

    @Test
    @DisplayName("POST without X-Operator-Reason → 400 REASON_REQUIRED; DELETE → 204")
    void reasonAndDelete() throws Exception {
        mockMvc.perform(post("/api/admin/groups/grp-1/domain-permission-sets")
                        .header("Authorization", bearer(HOLDER)).header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"setId\":\"s1\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        mockMvc.perform(delete("/api/admin/groups/grp-1/domain-permission-sets/s1")
                        .header("Authorization", bearer(HOLDER)).header("X-Operator-Reason", "r"))
                .andExpect(status().isNoContent());
        verify(useCase).revoke(any(), eq("grp-1"), eq("s1"), eq("r"));
    }

    @Test
    @DisplayName("error codes of the contract table: 403 TENANT_SCOPE_DENIED · 404 ×2 · 409 ×2 · 422")
    void errorCodes() throws Exception {
        when(useCase.grant(any()))
                .thenThrow(new TenantScopeDeniedException("x"))
                .thenThrow(new GroupNotFoundException("x"))
                .thenThrow(new DomainPermissionSetNotFoundException("x"))
                .thenThrow(new DomainPermissionSetAlreadyGrantedException("x"))
                .thenThrow(new IdempotencyKeyConflictException("x"))
                .thenThrow(new DomainNotSubscribedException("x"));
        String[][] expected = {{"403", "TENANT_SCOPE_DENIED"}, {"404", "GROUP_NOT_FOUND"},
                {"404", "DOMAIN_PERMISSION_SET_NOT_FOUND"}, {"409", "DOMAIN_PERMISSION_SET_ALREADY_GRANTED"},
                {"409", "IDEMPOTENCY_KEY_CONFLICT"}, {"422", "DOMAIN_NOT_SUBSCRIBED"}};
        for (String[] e : expected) {
            mockMvc.perform(post("/api/admin/groups/grp-1/domain-permission-sets")
                            .header("Authorization", bearer(HOLDER)).header("X-Operator-Reason", "r")
                            .header("Idempotency-Key", "k")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"setId\":\"s1\"}"))
                    .andExpect(status().is(Integer.parseInt(e[0])))
                    .andExpect(jsonPath("$.code").value(e[1]));
        }
    }
}
