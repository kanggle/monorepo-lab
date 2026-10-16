package com.example.admin.presentation;

import com.example.admin.application.ActionCode;
import com.example.admin.application.AdminActionAuditor;
import com.example.admin.application.CreateDomainPermissionSetCommand;
import com.example.admin.application.DomainPermissionSetResult;
import com.example.admin.application.DomainPermissionSetUseCase;
import com.example.admin.application.GrantDomainPermissionSetCommand;
import com.example.admin.application.GrantDomainPermissionSetResult;
import com.example.admin.application.OperatorDomainAccessResult;
import com.example.admin.application.OperatorDomainAccessUseCase;
import com.example.admin.application.exception.DomainNotSubscribedException;
import com.example.admin.application.exception.DomainPermissionSetAlreadyGrantedException;
import com.example.admin.application.exception.DomainPermissionSetInUseException;
import com.example.admin.application.exception.DomainPermissionSetNotFoundException;
import com.example.admin.application.exception.DomainPermissionSetReadOnlyException;
import com.example.admin.application.exception.DomainRoleNotAssignableException;
import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.DomainPermissionSetPort.SetView;
import com.example.admin.domain.rbac.DomainRoleCatalog;
import com.example.admin.domain.rbac.DomainRoleCatalog.RoleRef;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-631 — the HTTP shape of admin-api.md § 도메인 업무 권한 세트 (slice A endpoints): the
 * {@code domain_access.manage} gate and its DENIED row, the wire shapes (keys present with {@code null} under the
 * test profile's {@code non_null} inclusion), headers, and the error-code mapping of the six new codes.
 */
@WebMvcTest(controllers = {DomainPermissionSetController.class, OperatorDomainAccessController.class})
@ImportAutoConfiguration(AopAutoConfiguration.class)
@Import({SliceTestSecurityConfig.class, AdminExceptionHandler.class, RequiresPermissionAspect.class,
        DomainPermissionSetControllerSliceTest.JwtBeans.class})
@TestPropertySource(properties = {"admin.jwt.expected-token-type=admin",
        "spring.jackson.default-property-inclusion=non_null"})
class DomainPermissionSetControllerSliceTest {

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

    @MockitoBean DomainPermissionSetUseCase setUseCase;
    @MockitoBean OperatorDomainAccessUseCase accessUseCase;
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

    private static DomainPermissionSetResult tenantSet() {
        return new DomainPermissionSetResult(new SetView(10L, "set-1", "acme", null, "재고팀", "재고 조회 · 변경",
                List.of(new RoleRef("wms", "INVENTORY_READ"), new RoleRef("wms", "INVENTORY_WRITE")),
                Instant.parse("2026-10-14T09:00:00Z"), Instant.parse("2026-10-14T09:00:00Z")), 4, 1);
    }

    private static DomainPermissionSetResult preset() {
        return new DomainPermissionSetResult(new SetView(1L, "00000083-0000-7000-8000-000000000001", null,
                "wms-inventory-read", "재고 조회", null, List.of(new RoleRef("wms", "INVENTORY_READ")),
                Instant.EPOCH, Instant.EPOCH), 0, 0);
    }

    // ── gate ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("🔴 AC-3: no domain_access.manage → 403 PERMISSION_DENIED + DENIED row with the write's action code; use case untouched")
    void withoutPermission_403_deniedRow() throws Exception {
        mockMvc.perform(post("/api/admin/operators/op-x/domain-permission-sets")
                        .header("Authorization", bearer(NO_KEY))
                        .header("X-Operator-Reason", "r")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"setId\":\"s1\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PERMISSION_DENIED"));
        verify(adminActionAuditor).recordDenied(eq(ActionCode.OPERATOR_DOMAIN_SET_GRANT),
                eq(Permission.DOMAIN_ACCESS_MANAGE), anyString(), eq("POST"), isNull());

        mockMvc.perform(post("/api/admin/domain-permission-sets")
                        .header("Authorization", bearer(NO_KEY))
                        .header("X-Operator-Reason", "r")
                        .header("Idempotency-Key", "k")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"n\",\"roles\":[{\"domainKey\":\"wms\",\"role\":\"INVENTORY_READ\"}]}"))
                .andExpect(status().isForbidden());
        verify(adminActionAuditor).recordDenied(eq(ActionCode.DOMAIN_PERMISSION_SET_CREATE),
                eq(Permission.DOMAIN_ACCESS_MANAGE), anyString(), eq("POST"), isNull());

        mockMvc.perform(get("/api/admin/domain-roles").header("Authorization", bearer(NO_KEY)))
                .andExpect(status().isForbidden());
        verifyNoInteractions(setUseCase, accessUseCase);
    }

    // ── catalog / sets ──────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /domain-roles → {items:[…]} in catalog order with the wire fields")
    void catalog() throws Exception {
        when(setUseCase.catalog()).thenReturn(DomainRoleCatalog.entries());
        mockMvc.perform(get("/api/admin/domain-roles").header("Authorization", bearer(HOLDER)))
                .andExpect(status().isOk())
                // TASK-MONO-792: + the eight SCM roles, − SCM_OPERATOR · TASK-MONO-793: + the four ERP roles, − ERP_OPERATOR
                // TASK-MONO-794: + the five Finance roles, − FINANCE_OPERATOR
                // TASK-MONO-795: + the thirteen E-commerce roles, − ECOMMERCE_OPERATOR
                .andExpect(jsonPath("$.items.length()").value(40))
                .andExpect(jsonPath("$.items[0].domainKey").value("wms"))
                .andExpect(jsonPath("$.items[0].role").value("INVENTORY_READ"))
                .andExpect(jsonPath("$.items[0].access").value("READ"))
                .andExpect(jsonPath("$.items[0].fineGrained").value(true))
                .andExpect(jsonPath("$.items[0].label").value("재고 조회"))
                // items[8] = SCM_PROCUREMENT_READ (fine-grained since 792); items[16] = ERP_MASTERDATA_READ (since 793);
                // items[20] = FINANCE_ACCOUNT_READ (since 794); items[24] = FINANCE_LEDGER_CONTROL (access WRITE);
                // items[25] = ECOMMERCE_ORDER_READ (since 795); items[38] = FAN_OPERATOR (not yet)
                .andExpect(jsonPath("$.items[8].role").value("SCM_PROCUREMENT_READ"))
                .andExpect(jsonPath("$.items[8].fineGrained").value(true))
                .andExpect(jsonPath("$.items[16].role").value("ERP_MASTERDATA_READ"))
                .andExpect(jsonPath("$.items[16].fineGrained").value(true))
                .andExpect(jsonPath("$.items[20].role").value("FINANCE_ACCOUNT_READ"))
                .andExpect(jsonPath("$.items[20].fineGrained").value(true))
                .andExpect(jsonPath("$.items[24].role").value("FINANCE_LEDGER_CONTROL"))
                .andExpect(jsonPath("$.items[24].access").value("WRITE"))
                .andExpect(jsonPath("$.items[25].role").value("ECOMMERCE_ORDER_READ"))
                .andExpect(jsonPath("$.items[25].fineGrained").value(true))
                .andExpect(jsonPath("$.items[38].role").value("FAN_OPERATOR"))
                .andExpect(jsonPath("$.items[38].fineGrained").value(false));
    }

    @Test
    @DisplayName("GET /domain-permission-sets → preset (tenantId null, readOnly) + tenant set (domains, counts) — keys present")
    void listSets_wireShape() throws Exception {
        when(setUseCase.list(any(), eq("acme"))).thenReturn(List.of(preset(), tenantSet()));
        mockMvc.perform(get("/api/admin/domain-permission-sets")
                        .header("Authorization", bearer(HOLDER)).header("X-Tenant-Id", "acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].tenantId").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[0].systemKey").value("wms-inventory-read"))
                .andExpect(jsonPath("$.items[0].readOnly").value(true))
                .andExpect(jsonPath("$.items[0].description").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[1].tenantId").value("acme"))
                .andExpect(jsonPath("$.items[1].systemKey").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[1].readOnly").value(false))
                .andExpect(jsonPath("$.items[1].roles[1].role").value("INVENTORY_WRITE"))
                .andExpect(jsonPath("$.items[1].domains[0]").value("wms"))
                .andExpect(jsonPath("$.items[1].grantCount").value(4))
                .andExpect(jsonPath("$.items[1].groupCount").value(1))
                .andExpect(jsonPath("$.items[1].createdAt").value("2026-10-14T09:00:00Z"));
    }

    @Test
    @DisplayName("POST /domain-permission-sets → 201; reason percent-decoded, headers and roles reach the command")
    void createSet_201() throws Exception {
        when(setUseCase.create(any())).thenReturn(tenantSet());
        mockMvc.perform(post("/api/admin/domain-permission-sets")
                        .header("Authorization", bearer(HOLDER))
                        .header("X-Tenant-Id", "acme")
                        .header("X-Operator-Reason", "%EC%9E%AC%EA%B3%A0")
                        .header("Idempotency-Key", "k-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"재고팀\",\"roles\":[{\"domainKey\":\"wms\",\"role\":\"INVENTORY_READ\"}]}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.setId").value("set-1"));
        ArgumentCaptor<CreateDomainPermissionSetCommand> cmd = ArgumentCaptor.forClass(CreateDomainPermissionSetCommand.class);
        verify(setUseCase).create(cmd.capture());
        assertThat(cmd.getValue().requestedTenantId()).isEqualTo("acme");
        assertThat(cmd.getValue().reason()).isEqualTo("재고");
        assertThat(cmd.getValue().idempotencyKey()).isEqualTo("k-1");
        assertThat(cmd.getValue().roles()).containsExactly(new RoleRef("wms", "INVENTORY_READ"));
    }

    @Test
    @DisplayName("writes without X-Operator-Reason → 400 REASON_REQUIRED, use case untouched")
    void missingReason_400() throws Exception {
        mockMvc.perform(delete("/api/admin/domain-permission-sets/set-1").header("Authorization", bearer(HOLDER)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REASON_REQUIRED"));
        verify(setUseCase, never()).delete(any(), any(), any(), any());
    }

    @Test
    @DisplayName("the six codes map to their statuses: 404 · 409 ×3 · 422 ×2 — and 503 when entitlement is unreadable")
    void errorCodeMapping() throws Exception {
        when(setUseCase.update(any()))
                .thenThrow(new DomainPermissionSetReadOnlyException("preset"))
                .thenThrow(new DomainRoleNotAssignableException("x"))
                .thenThrow(new DomainNotSubscribedException("x"))
                .thenThrow(new DomainPermissionSetNotFoundException("x"))
                .thenThrow(new DownstreamFailureException("down", null));
        String[][] expected = {{"409", "DOMAIN_PERMISSION_SET_READ_ONLY"}, {"422", "DOMAIN_ROLE_NOT_ASSIGNABLE"},
                {"422", "DOMAIN_NOT_SUBSCRIBED"}, {"404", "DOMAIN_PERMISSION_SET_NOT_FOUND"},
                {"503", "DOWNSTREAM_ERROR"}};
        for (String[] e : expected) {
            mockMvc.perform(patch("/api/admin/domain-permission-sets/set-1")
                            .header("Authorization", bearer(HOLDER))
                            .header("X-Operator-Reason", "r")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"name\":\"n\"}"))
                    .andExpect(status().is(Integer.parseInt(e[0])))
                    .andExpect(jsonPath("$.code").value(e[1]));
        }
        org.mockito.Mockito.doThrow(new DomainPermissionSetInUseException("x"))
                .when(setUseCase).delete(any(), any(), eq("set-1"), any());
        mockMvc.perform(delete("/api/admin/domain-permission-sets/set-1")
                        .header("Authorization", bearer(HOLDER)).header("X-Operator-Reason", "r"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOMAIN_PERMISSION_SET_IN_USE"));
        when(accessUseCase.grant(any())).thenThrow(new DomainPermissionSetAlreadyGrantedException("x"));
        mockMvc.perform(post("/api/admin/operators/op-x/domain-permission-sets")
                        .header("Authorization", bearer(HOLDER)).header("X-Operator-Reason", "r")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"setId\":\"s1\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DOMAIN_PERMISSION_SET_ALREADY_GRANTED"));
    }

    // ── operator grants ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST …/operators/{id}/domain-permission-sets → 201 {operatorId, tenantId, setId, source, grantedAt, grantedBy, selfGrant}")
    void grant_201() throws Exception {
        when(accessUseCase.grant(any())).thenReturn(new GrantDomainPermissionSetResult("op-x", "acme", "s1",
                "DIRECT", Instant.parse("2026-10-14T09:00:00Z"), HOLDER, false));
        mockMvc.perform(post("/api/admin/operators/op-x/domain-permission-sets")
                        .header("Authorization", bearer(HOLDER))
                        .header("X-Tenant-Id", "acme")
                        .header("X-Operator-Reason", "r")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"setId\":\"s1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.source").value("DIRECT"))
                .andExpect(jsonPath("$.tenantId").value("acme"))
                .andExpect(jsonPath("$.selfGrant").value(false))
                .andExpect(jsonPath("$.grantedBy").value(HOLDER));
        ArgumentCaptor<GrantDomainPermissionSetCommand> cmd = ArgumentCaptor.forClass(GrantDomainPermissionSetCommand.class);
        verify(accessUseCase).grant(cmd.capture());
        assertThat(cmd.getValue().operatorId()).isEqualTo("op-x");
        assertThat(cmd.getValue().setId()).isEqualTo("s1");
    }

    @Test
    @DisplayName("grant refusals: other tenant 403 TENANT_SCOPE_DENIED · non-member 404 OPERATOR_NOT_FOUND")
    void grant_refusals() throws Exception {
        when(accessUseCase.grant(any()))
                .thenThrow(new TenantScopeDeniedException("no"))
                .thenThrow(new OperatorNotFoundException("no"));
        for (String[] e : new String[][] {{"403", "TENANT_SCOPE_DENIED"}, {"404", "OPERATOR_NOT_FOUND"}}) {
            mockMvc.perform(post("/api/admin/operators/op-x/domain-permission-sets")
                            .header("Authorization", bearer(HOLDER)).header("X-Operator-Reason", "r")
                            .contentType(MediaType.APPLICATION_JSON).content("{\"setId\":\"s1\"}"))
                    .andExpect(status().is(Integer.parseInt(e[0])))
                    .andExpect(jsonPath("$.code").value(e[1]));
        }
    }

    @Test
    @DisplayName("DELETE …/operators/{id}/domain-permission-sets/{setId} → 204")
    void revoke_204() throws Exception {
        mockMvc.perform(delete("/api/admin/operators/op-x/domain-permission-sets/s1")
                        .header("Authorization", bearer(HOLDER))
                        .header("X-Tenant-Id", "acme")
                        .header("X-Operator-Reason", "r"))
                .andExpect(status().isNoContent());
        verify(accessUseCase).revoke(any());
    }

    @Test
    @DisplayName("GET …/domain-access → roles + sets[].sources (GROUP carries groupId/groupName; MIGRATION grantedBy null, present)")
    void domainAccess_wireShape() throws Exception {
        when(accessUseCase.domainAccess(any(), eq("acme"), eq("op-x"))).thenReturn(new OperatorDomainAccessResult(
                "op-x", "acme", List.of(new RoleRef("wms", "INVENTORY_READ")),
                List.of(new OperatorDomainAccessResult.SetAccess("s1", "재고팀", null, List.of(
                        new OperatorDomainAccessResult.Source("MIGRATION", null, null, "acme",
                                Instant.parse("2026-10-14T00:00:00Z"), null),
                        new OperatorDomainAccessResult.Source("GROUP", "g-1", "재고팀", "acme",
                                Instant.parse("2026-10-14T09:05:00Z"), "op-y"))))));
        mockMvc.perform(get("/api/admin/operators/op-x/domain-access")
                        .header("Authorization", bearer(HOLDER)).header("X-Tenant-Id", "acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0].role").value("INVENTORY_READ"))
                .andExpect(jsonPath("$.sets[0].systemKey").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.sets[0].sources[0].source").value("MIGRATION"))
                .andExpect(jsonPath("$.sets[0].sources[0].grantedBy").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.sets[0].sources[0].groupId").doesNotExist())
                .andExpect(jsonPath("$.sets[0].sources[1].groupId").value("g-1"))
                .andExpect(jsonPath("$.sets[0].sources[1].groupName").value("재고팀"));
    }

    @Test
    @DisplayName("GET /me/domain-access needs no permission key (any operator) and X-Tenant-Id (missing → 400)")
    void me_noPermissionKey_tenantRequired() throws Exception {
        when(accessUseCase.myDomainAccess(any(), eq("acme")))
                .thenReturn(new OperatorDomainAccessResult(NO_KEY, "acme", List.of(), List.of()));
        mockMvc.perform(get("/api/admin/me/domain-access")
                        .header("Authorization", bearer(NO_KEY)).header("X-Tenant-Id", "acme"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.operatorId").value(NO_KEY))
                .andExpect(jsonPath("$.roles").isEmpty());
        mockMvc.perform(get("/api/admin/me/domain-access").header("Authorization", bearer(NO_KEY)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }
}
