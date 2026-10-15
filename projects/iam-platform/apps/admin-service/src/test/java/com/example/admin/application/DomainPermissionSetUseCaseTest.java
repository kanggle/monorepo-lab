package com.example.admin.application;

import com.example.admin.application.exception.DomainNotSubscribedException;
import com.example.admin.application.exception.DomainPermissionSetInUseException;
import com.example.admin.application.exception.DomainPermissionSetNotFoundException;
import com.example.admin.application.exception.DomainPermissionSetReadOnlyException;
import com.example.admin.application.exception.DomainRoleNotAssignableException;
import com.example.admin.application.exception.DownstreamFailureException;
import com.example.admin.application.exception.IdempotencyKeyConflictException;
import com.example.admin.application.exception.ReasonRequiredException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.DomainPermissionSetPort;
import com.example.admin.application.port.DomainPermissionSetPort.SetView;
import com.example.admin.domain.rbac.DomainRoleCatalog.RoleRef;
import com.example.admin.domain.rbac.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-631 — {@link DomainPermissionSetUseCase}: the acting-tenant gate, the catalog (422
 * DOMAIN_ROLE_NOT_ASSIGNABLE) and entitlement (422 DOMAIN_NOT_SUBSCRIBED / 503) bounds, presets read-only, set in use,
 * cross-tenant 404, and the audit row of every write. Every refusal writes nothing.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class DomainPermissionSetUseCaseTest {

    private static final OperatorContext ACTOR = new OperatorContext("op-admin-acme", "jti");
    private static final List<RoleRef> INVENTORY = List.of(
            new RoleRef("wms", "INVENTORY_WRITE"), new RoleRef("wms", "INVENTORY_READ"));

    @Mock DomainPermissionSetPort setPort;
    @Mock AdminOperatorPort operatorPort;
    @Mock QueryTenantScopeGate tenantGate;
    @Mock DomainSetEntitlementGuard entitlementGuard;
    @Mock AdminActionAuditor auditor;

    @InjectMocks DomainPermissionSetUseCase useCase;

    private static SetView tenantSet(long id, String setId, String tenantId, List<RoleRef> roles) {
        return new SetView(id, setId, tenantId, null, "재고팀", null, roles, Instant.EPOCH, Instant.EPOCH);
    }

    private static SetView preset(long id, String key) {
        return new SetView(id, "preset-" + key, null, key, key, null, List.of(new RoleRef("wms", "INVENTORY_READ")),
                Instant.EPOCH, Instant.EPOCH);
    }

    private void writeGate(ActionCode code, String requested, String resolved) {
        when(tenantGate.resolve(ACTOR, requested, code, Permission.DOMAIN_ACCESS_MANAGE))
                .thenReturn(new QueryTenantScopeGate.Resolved(resolved, "*".equals(resolved)));
    }

    // ── create ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create: roles sorted into catalog order, entitlement checked on their domains, row + audit (DOMAIN_PERMISSION_SET_CREATE)")
    void create_happyPath() {
        writeGate(ActionCode.DOMAIN_PERMISSION_SET_CREATE, "acme", "acme");
        when(auditor.isIdempotencyKeyUsed(ACTOR.operatorId(), ActionCode.DOMAIN_PERMISSION_SET_CREATE, "k1"))
                .thenReturn(false);
        when(operatorPort.resolveActorInternalId(ACTOR.operatorId())).thenReturn(5L);
        List<RoleRef> sorted = List.of(new RoleRef("wms", "INVENTORY_READ"), new RoleRef("wms", "INVENTORY_WRITE"));
        when(setPort.createTenantSet(anyString(), eq("acme"), eq("재고팀"), eq("desc"), eq(sorted), eq(5L), any()))
                .thenReturn(tenantSet(10L, "set-1", "acme", sorted));
        when(auditor.newAuditId()).thenReturn("audit-1");

        DomainPermissionSetResult r = useCase.create(new CreateDomainPermissionSetCommand(
                ACTOR, "acme", "  재고팀  ", "desc", INVENTORY, "reason", "k1"));

        assertThat(r.set().setId()).isEqualTo("set-1");
        assertThat(r.grantCount()).isZero();
        verify(entitlementGuard).requireEntitled("acme", List.of("wms"));
        ArgumentCaptor<AdminActionAuditor.AuditRecord> rec = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).recordWithPermission(rec.capture(), eq(Permission.DOMAIN_ACCESS_MANAGE));
        assertThat(rec.getValue().actionCode()).isEqualTo(ActionCode.DOMAIN_PERMISSION_SET_CREATE);
        assertThat(rec.getValue().targetType()).isEqualTo("DOMAIN_PERMISSION_SET");
        assertThat(rec.getValue().targetId()).isEqualTo("set-1");
        assertThat(rec.getValue().targetTenantId()).isEqualTo("acme");
        assertThat(rec.getValue().idempotencyKey()).isEqualTo("k1");
        assertThat(rec.getValue().downstreamDetail()).isEqualTo("roles=wms:INVENTORY_READ,wms:INVENTORY_WRITE");
    }

    @Test
    @DisplayName("create in '*' → 400 (a tenant set belongs to a real tenant), nothing written")
    void create_platformTenant_400() {
        writeGate(ActionCode.DOMAIN_PERMISSION_SET_CREATE, null, "*");
        assertThatThrownBy(() -> useCase.create(new CreateDomainPermissionSetCommand(
                ACTOR, null, "n", null, INVENTORY, "r", "k")))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(setPort, entitlementGuard);
    }

    @Test
    @DisplayName("create with an administrative / unknown role → 422 DOMAIN_ROLE_NOT_ASSIGNABLE before entitlement, nothing written")
    void create_roleOutsideCatalog_422() {
        writeGate(ActionCode.DOMAIN_PERMISSION_SET_CREATE, "acme", "acme");
        assertThatThrownBy(() -> useCase.create(new CreateDomainPermissionSetCommand(ACTOR, "acme", "n", null,
                List.of(new RoleRef("wms", "INVENTORY_READ"), new RoleRef("wms", "WMS_ADMIN")), "r", "k")))
                .isInstanceOf(DomainRoleNotAssignableException.class);
        verifyNoInteractions(setPort, entitlementGuard);
    }

    @Test
    @DisplayName("AC-3: create covering an unsubscribed domain → 422 DOMAIN_NOT_SUBSCRIBED, nothing written")
    void create_unsubscribed_422() {
        writeGate(ActionCode.DOMAIN_PERMISSION_SET_CREATE, "acme", "acme");
        when(auditor.isIdempotencyKeyUsed(any(), any(), any())).thenReturn(false);
        org.mockito.Mockito.doThrow(new DomainNotSubscribedException("scm"))
                .when(entitlementGuard).requireEntitled("acme", List.of("scm"));
        assertThatThrownBy(() -> useCase.create(new CreateDomainPermissionSetCommand(ACTOR, "acme", "n", null,
                List.of(new RoleRef("scm", "SCM_PROCUREMENT_READ")), "r", "k"))) // TASK-MONO-792: a catalog scm row
                .isInstanceOf(DomainNotSubscribedException.class);
        verify(setPort, never()).createTenantSet(any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("create when entitlement cannot be read → 503 (DownstreamFailureException), nothing written")
    void create_entitlementUnavailable_503() {
        writeGate(ActionCode.DOMAIN_PERMISSION_SET_CREATE, "acme", "acme");
        when(auditor.isIdempotencyKeyUsed(any(), any(), any())).thenReturn(false);
        org.mockito.Mockito.doThrow(new DownstreamFailureException("down", null))
                .when(entitlementGuard).requireEntitled(eq("acme"), any());
        assertThatThrownBy(() -> useCase.create(new CreateDomainPermissionSetCommand(ACTOR, "acme", "n", null,
                INVENTORY, "r", "k")))
                .isInstanceOf(DownstreamFailureException.class);
        verify(setPort, never()).createTenantSet(any(), any(), any(), any(), any(), any(), any());
        verify(auditor, never()).recordWithPermission(any(), any());
    }

    @Test
    @DisplayName("create: re-sent Idempotency-Key → 409, missing key → 400, blank name → 400, missing reason → 400")
    void create_requestShapeRefusals() {
        writeGate(ActionCode.DOMAIN_PERMISSION_SET_CREATE, "acme", "acme");
        when(auditor.isIdempotencyKeyUsed(ACTOR.operatorId(), ActionCode.DOMAIN_PERMISSION_SET_CREATE, "used"))
                .thenReturn(true);
        assertThatThrownBy(() -> useCase.create(new CreateDomainPermissionSetCommand(ACTOR, "acme", "n", null,
                INVENTORY, "r", "used"))).isInstanceOf(IdempotencyKeyConflictException.class);
        assertThatThrownBy(() -> useCase.create(new CreateDomainPermissionSetCommand(ACTOR, "acme", "n", null,
                INVENTORY, "r", null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.create(new CreateDomainPermissionSetCommand(ACTOR, "acme", "   ", null,
                INVENTORY, "r", "k2"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.create(new CreateDomainPermissionSetCommand(ACTOR, "acme", "n", null,
                List.of(), "r", "k3"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.create(new CreateDomainPermissionSetCommand(ACTOR, "acme", "n", null,
                INVENTORY, " ", "k4"))).isInstanceOf(ReasonRequiredException.class);
        verifyNoInteractions(setPort);
    }

    @Test
    @DisplayName("create out of grant scope → the gate's 403 propagates, nothing written")
    void create_outOfScope_403() {
        when(tenantGate.resolve(ACTOR, "globex", ActionCode.DOMAIN_PERMISSION_SET_CREATE,
                Permission.DOMAIN_ACCESS_MANAGE)).thenThrow(new TenantScopeDeniedException("no"));
        assertThatThrownBy(() -> useCase.create(new CreateDomainPermissionSetCommand(ACTOR, "globex", "n", null,
                INVENTORY, "r", "k"))).isInstanceOf(TenantScopeDeniedException.class);
        verifyNoInteractions(setPort, entitlementGuard);
    }

    // ── update / delete ─────────────────────────────────────────────────────

    @Test
    @DisplayName("update a preset → 409 READ_ONLY; another tenant's set → 404; nothing written")
    void update_presetAndForeignSet() {
        writeGate(ActionCode.DOMAIN_PERMISSION_SET_UPDATE, "acme", "acme");
        when(setPort.findBySetId("preset-legacy-compat")).thenReturn(Optional.of(preset(1L, "legacy-compat")));
        when(setPort.findBySetId("globex-set")).thenReturn(Optional.of(tenantSet(2L, "globex-set", "globex", INVENTORY)));

        assertThatThrownBy(() -> useCase.update(new UpdateDomainPermissionSetCommand(ACTOR, "acme",
                "preset-legacy-compat", "x", null, null, "r"))).isInstanceOf(DomainPermissionSetReadOnlyException.class);
        assertThatThrownBy(() -> useCase.update(new UpdateDomainPermissionSetCommand(ACTOR, "acme",
                "globex-set", "x", null, null, "r"))).isInstanceOf(DomainPermissionSetNotFoundException.class);
        verify(setPort, never()).updateTenantSet(anyLong(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("update roles → entitlement re-checked, roles replaced, audit carries before/after")
    void update_roles_rechecksEntitlement() {
        writeGate(ActionCode.DOMAIN_PERMISSION_SET_UPDATE, "acme", "acme");
        SetView before = tenantSet(3L, "s3", "acme", List.of(new RoleRef("wms", "INVENTORY_READ")));
        List<RoleRef> newRoles = List.of(new RoleRef("wms", "OUTBOUND_READ"));
        when(setPort.findBySetId("s3")).thenReturn(Optional.of(before));
        when(setPort.updateTenantSet(eq(3L), eq(null), eq(null), eq(newRoles), any()))
                .thenReturn(tenantSet(3L, "s3", "acme", newRoles));
        when(setPort.countGrantRows(3L, "acme")).thenReturn(2L);
        when(setPort.countGroupGrants(3L, "acme")).thenReturn(0L);
        when(auditor.newAuditId()).thenReturn("a");

        DomainPermissionSetResult r = useCase.update(new UpdateDomainPermissionSetCommand(ACTOR, "acme", "s3",
                null, null, newRoles, "r"));

        assertThat(r.grantCount()).isEqualTo(2L);
        verify(entitlementGuard).requireEntitled("acme", List.of("wms"));
        ArgumentCaptor<AdminActionAuditor.AuditRecord> rec = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).recordWithPermission(rec.capture(), eq(Permission.DOMAIN_ACCESS_MANAGE));
        assertThat(rec.getValue().downstreamDetail())
                .isEqualTo("changed=roles rolesBefore=wms:INVENTORY_READ rolesAfter=wms:OUTBOUND_READ");
    }

    @Test
    @DisplayName("update with no field → 400")
    void update_noField_400() {
        writeGate(ActionCode.DOMAIN_PERMISSION_SET_UPDATE, "acme", "acme");
        assertThatThrownBy(() -> useCase.update(new UpdateDomainPermissionSetCommand(ACTOR, "acme", "s3",
                null, null, null, "r"))).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(setPort);
    }

    @Test
    @DisplayName("delete while granted (rows of any source or a group) → 409 IN_USE, nothing deleted")
    void delete_inUse_409() {
        writeGate(ActionCode.DOMAIN_PERMISSION_SET_DELETE, "acme", "acme");
        when(setPort.findBySetId("s4")).thenReturn(Optional.of(tenantSet(4L, "s4", "acme", INVENTORY)));
        when(setPort.countAllGrantRows(4L)).thenReturn(0L);
        when(setPort.countAllGroupGrants(4L)).thenReturn(1L);

        assertThatThrownBy(() -> useCase.delete(ACTOR, "acme", "s4", "r"))
                .isInstanceOf(DomainPermissionSetInUseException.class);
        verify(setPort, never()).deleteSet(anyLong());
    }

    @Test
    @DisplayName("delete an unused tenant set → row deleted + DOMAIN_PERMISSION_SET_DELETE audit")
    void delete_happy() {
        writeGate(ActionCode.DOMAIN_PERMISSION_SET_DELETE, "acme", "acme");
        when(setPort.findBySetId("s5")).thenReturn(Optional.of(tenantSet(5L, "s5", "acme", INVENTORY)));
        when(setPort.countAllGrantRows(5L)).thenReturn(0L);
        when(setPort.countAllGroupGrants(5L)).thenReturn(0L);
        when(auditor.newAuditId()).thenReturn("a");

        useCase.delete(ACTOR, "acme", "s5", "r");

        verify(setPort).deleteSet(5L);
        ArgumentCaptor<AdminActionAuditor.AuditRecord> rec = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).recordWithPermission(rec.capture(), eq(Permission.DOMAIN_ACCESS_MANAGE));
        assertThat(rec.getValue().actionCode()).isEqualTo(ActionCode.DOMAIN_PERMISSION_SET_DELETE);
    }

    // ── reads ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("list: presets first in contract order, then T's tenant sets; T = '*' → presets only")
    void list_presetsThenTenantSets() {
        when(tenantGate.resolveWithoutDenyRow(ACTOR, "acme", Permission.DOMAIN_ACCESS_MANAGE))
                .thenReturn(new QueryTenantScopeGate.Resolved("acme", false));
        when(tenantGate.resolveWithoutDenyRow(ACTOR, null, Permission.DOMAIN_ACCESS_MANAGE))
                .thenReturn(new QueryTenantScopeGate.Resolved("*", true));
        when(setPort.findPresets()).thenReturn(List.of(preset(15L, "legacy-compat"), preset(1L, "wms-inventory-read")));
        when(setPort.findTenantSets("acme")).thenReturn(List.of(tenantSet(20L, "t1", "acme", INVENTORY)));

        assertThat(useCase.list(ACTOR, "acme")).extracting(r -> r.set().setId())
                .containsExactly("preset-wms-inventory-read", "preset-legacy-compat", "t1");
        assertThat(useCase.list(ACTOR, null)).extracting(r -> r.set().setId())
                .containsExactly("preset-wms-inventory-read", "preset-legacy-compat");
        verify(setPort, never()).findTenantSets("*");
    }

    @Test
    @DisplayName("get another tenant's set → 404 (never disclosed); a missing one → 404")
    void get_foreignOrMissing_404() {
        when(tenantGate.resolveWithoutDenyRow(ACTOR, "acme", Permission.DOMAIN_ACCESS_MANAGE))
                .thenReturn(new QueryTenantScopeGate.Resolved("acme", false));
        when(setPort.findBySetId("g")).thenReturn(Optional.of(tenantSet(9L, "g", "globex", INVENTORY)));
        when(setPort.findBySetId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.get(ACTOR, "acme", "g")).isInstanceOf(DomainPermissionSetNotFoundException.class);
        assertThatThrownBy(() -> useCase.get(ACTOR, "acme", "missing"))
                .isInstanceOf(DomainPermissionSetNotFoundException.class);
    }

    @Test
    @DisplayName("catalog: the whole DomainRoleCatalog, no tenant gate")
    void catalog_noGate() {
        // TASK-MONO-792: SCM_OPERATOR → eight SCM roles · TASK-MONO-793: ERP_OPERATOR → four ERP roles
        assertThat(useCase.catalog()).hasSize(24);
        verifyNoInteractions(tenantGate);
    }
}
