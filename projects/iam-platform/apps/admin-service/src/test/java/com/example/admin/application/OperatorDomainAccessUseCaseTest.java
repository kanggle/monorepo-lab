package com.example.admin.application;

import com.example.admin.application.exception.DomainNotSubscribedException;
import com.example.admin.application.exception.DomainPermissionSetAlreadyGrantedException;
import com.example.admin.application.exception.DomainPermissionSetNotFoundException;
import com.example.admin.application.exception.OperatorNotFoundException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.DomainPermissionSetPort;
import com.example.admin.application.port.DomainPermissionSetPort.GrantRowView;
import com.example.admin.application.port.DomainPermissionSetPort.SetView;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-631 (ADR-MONO-083 D4 · D5 · OD-3 · OD-4) — {@link OperatorDomainAccessUseCase}: the six-step grant order,
 * the {@code '*'} rules, the personal revoke that keeps GROUP rows (slice-A half of AC-1), and the effective-access
 * read. Every refusal writes nothing.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class OperatorDomainAccessUseCaseTest {

    private static final OperatorContext ADMIN = new OperatorContext("op-admin", "jti");

    @Mock DomainPermissionSetPort setPort;
    @Mock AdminOperatorPort operatorPort;
    @Mock OperatorTenantAssignmentPort assignmentPort;
    @Mock QueryTenantScopeGate tenantGate;
    @Mock TenantScopeGuard tenantScopeGuard;
    @Mock DomainSetEntitlementGuard entitlementGuard;
    @Mock AdminActionAuditor auditor;

    @InjectMocks OperatorDomainAccessUseCase useCase;

    private static AdminOperatorPort.OperatorView op(long id, String operatorId, String home) {
        return new AdminOperatorPort.OperatorView(id, operatorId, home, operatorId + "@x", "h", "Op", "ACTIVE",
                null, null, Instant.EPOCH, Instant.EPOCH, null, null);
    }

    private static SetView tenantSet(long id, String setId, String tenant, RoleRef... roles) {
        return new SetView(id, setId, tenant, null, "set " + setId, null, List.of(roles), Instant.EPOCH, Instant.EPOCH);
    }

    private static SetView preset(long id, String key, RoleRef... roles) {
        return new SetView(id, "preset-" + key, null, key, key, null, List.of(roles), Instant.EPOCH, Instant.EPOCH);
    }

    private void gate(ActionCode code, String requested, String resolved, boolean platform) {
        when(tenantGate.resolve(ADMIN, requested, code, Permission.DOMAIN_ACCESS_MANAGE))
                .thenReturn(new QueryTenantScopeGate.Resolved(resolved, platform));
    }

    private void member(AdminOperatorPort.OperatorView target, String... tenants) {
        when(operatorPort.findByOperatorId(target.operatorId())).thenReturn(Optional.of(target));
        when(assignmentPort.findAssignedTenantIds(target.internalId())).thenReturn(Set.of(tenants));
    }

    private GrantDomainPermissionSetCommand grant(String tenant, String operatorId, String setId) {
        return new GrantDomainPermissionSetCommand(ADMIN, tenant, operatorId, setId, "reason");
    }

    // ── grant ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("grant: member of T, T's own set, subscribed → DIRECT row + audit «setId=… source=DIRECT selfGrant=false»")
    void grant_happyPath() {
        gate(ActionCode.OPERATOR_DOMAIN_SET_GRANT, "acme", "acme", false);
        AdminOperatorPort.OperatorView target = op(7L, "op-target", "acme");
        member(target, "acme");
        when(setPort.findBySetId("s1")).thenReturn(Optional.of(tenantSet(11L, "s1", "acme",
                new RoleRef("wms", "INVENTORY_READ"))));
        when(setPort.grantExists(7L, "acme", 11L, "DIRECT")).thenReturn(false);
        when(operatorPort.resolveActorInternalId("op-admin")).thenReturn(2L);
        when(auditor.newAuditId()).thenReturn("a");

        GrantDomainPermissionSetResult r = useCase.grant(grant("acme", "op-target", "s1"));

        assertThat(r.source()).isEqualTo("DIRECT");
        assertThat(r.selfGrant()).isFalse();
        assertThat(r.grantedBy()).isEqualTo("op-admin");
        verify(entitlementGuard).requireEntitled("acme", List.of("wms"));
        verify(setPort).insertGrant(eq(7L), eq("acme"), eq(11L), eq("DIRECT"), eq(null), eq(2L), any());
        ArgumentCaptor<AdminActionAuditor.AuditRecord> rec = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).recordWithPermission(rec.capture(), eq(Permission.DOMAIN_ACCESS_MANAGE));
        assertThat(rec.getValue().actionCode()).isEqualTo(ActionCode.OPERATOR_DOMAIN_SET_GRANT);
        assertThat(rec.getValue().targetType()).isEqualTo("OPERATOR");
        assertThat(rec.getValue().targetId()).isEqualTo("op-target");
        assertThat(rec.getValue().targetTenantId()).isEqualTo("acme");
        assertThat(rec.getValue().downstreamDetail()).isEqualTo("setId=s1 source=DIRECT selfGrant=false");
    }

    @Test
    @DisplayName("OD-4: granting to oneself is allowed and marked selfGrant=true")
    void grant_self_marked() {
        gate(ActionCode.OPERATOR_DOMAIN_SET_GRANT, "acme", "acme", false);
        AdminOperatorPort.OperatorView self = op(2L, "op-admin", "acme");
        member(self, "acme");
        when(setPort.findBySetId("p1")).thenReturn(Optional.of(preset(1L, "wms-inventory-read",
                new RoleRef("wms", "INVENTORY_READ"))));
        when(setPort.grantExists(2L, "acme", 1L, "DIRECT")).thenReturn(false);
        when(operatorPort.resolveActorInternalId("op-admin")).thenReturn(2L);
        when(auditor.newAuditId()).thenReturn("a");

        assertThat(useCase.grant(grant("acme", "op-admin", "p1")).selfGrant()).isTrue();
        ArgumentCaptor<AdminActionAuditor.AuditRecord> rec = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).recordWithPermission(rec.capture(), any());
        assertThat(rec.getValue().downstreamDetail()).endsWith("selfGrant=true");
    }

    @Test
    @DisplayName("AC-3: another tenant (outside the grant scope) → the gate's 403, nothing read or written")
    void grant_otherTenant_403() {
        when(tenantGate.resolve(ADMIN, "globex", ActionCode.OPERATOR_DOMAIN_SET_GRANT, Permission.DOMAIN_ACCESS_MANAGE))
                .thenThrow(new TenantScopeDeniedException("no"));
        assertThatThrownBy(() -> useCase.grant(grant("globex", "op-target", "s1")))
                .isInstanceOf(TenantScopeDeniedException.class);
        verifyNoInteractions(setPort, operatorPort, entitlementGuard);
    }

    @Test
    @DisplayName("target not a member of T → 404 OPERATOR_NOT_FOUND (another company's membership not disclosed)")
    void grant_nonMember_404() {
        gate(ActionCode.OPERATOR_DOMAIN_SET_GRANT, "acme", "acme", false);
        member(op(7L, "op-target", "globex"), "globex");
        assertThatThrownBy(() -> useCase.grant(grant("acme", "op-target", "s1")))
                .isInstanceOf(OperatorNotFoundException.class);
        verifyNoInteractions(setPort);
    }

    @Test
    @DisplayName("non-'*' target with T = '*' → 400; unknown target → 404")
    void grant_starRowForCompanyOperator_400() {
        gate(ActionCode.OPERATOR_DOMAIN_SET_GRANT, "*", "*", true);
        when(operatorPort.findByOperatorId("op-target")).thenReturn(Optional.of(op(7L, "op-target", "acme")));
        when(operatorPort.findByOperatorId("ghost")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.grant(grant("*", "op-target", "p1")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.grant(grant("*", "ghost", "p1")))
                .isInstanceOf(OperatorNotFoundException.class);
        verifyNoInteractions(setPort);
    }

    @Test
    @DisplayName("OD-3: a '*' target without the actor's '*' grant scope → 403 + DENIED row, nothing written")
    void grant_starTarget_needsStarScope() {
        gate(ActionCode.OPERATOR_DOMAIN_SET_GRANT, "acme", "acme", false);
        when(operatorPort.findByOperatorId("op-platform")).thenReturn(Optional.of(op(9L, "op-platform", "*")));

        assertThatThrownBy(() -> useCase.grant(grant("acme", "op-platform", "p1")))
                .isInstanceOf(TenantScopeDeniedException.class);
        verify(tenantScopeGuard).recordTenantDenied(ADMIN, Permission.DOMAIN_ACCESS_MANAGE, "acme",
                ActionCode.OPERATOR_DOMAIN_SET_GRANT);
        verifyNoInteractions(setPort);
    }

    @Test
    @DisplayName("OD-3: '*' scope → a '*' row with a PRESET (no entitlement check, no membership); a tenant set on '*' → 404")
    void grant_starTarget_starRow() {
        gate(ActionCode.OPERATOR_DOMAIN_SET_GRANT, "*", "*", true);
        when(operatorPort.findByOperatorId("op-platform")).thenReturn(Optional.of(op(9L, "op-platform", "*")));
        when(setPort.findBySetId("p-fan")).thenReturn(Optional.of(preset(12L, "fan-full",
                new RoleRef("fan", "FAN_OPERATOR"))));
        when(setPort.findBySetId("t1")).thenReturn(Optional.of(tenantSet(20L, "t1", "acme",
                new RoleRef("wms", "INVENTORY_READ"))));
        when(setPort.grantExists(9L, "*", 12L, "DIRECT")).thenReturn(false);
        when(operatorPort.resolveActorInternalId("op-admin")).thenReturn(2L);
        when(auditor.newAuditId()).thenReturn("a");

        GrantDomainPermissionSetResult r = useCase.grant(grant("*", "op-platform", "p-fan"));
        assertThat(r.tenantId()).isEqualTo("*");
        verify(setPort).insertGrant(eq(9L), eq("*"), eq(12L), eq("DIRECT"), eq(null), eq(2L), any());
        verifyNoInteractions(entitlementGuard, assignmentPort);

        assertThatThrownBy(() -> useCase.grant(grant("*", "op-platform", "t1")))
                .isInstanceOf(DomainPermissionSetNotFoundException.class);
    }

    @Test
    @DisplayName("set: another tenant's → 404 · legacy-compat → 400 · missing → 404 — nothing written")
    void grant_setRefusals() {
        gate(ActionCode.OPERATOR_DOMAIN_SET_GRANT, "acme", "acme", false);
        member(op(7L, "op-target", "acme"), "acme");
        when(setPort.findBySetId("g1")).thenReturn(Optional.of(tenantSet(30L, "g1", "globex",
                new RoleRef("wms", "INVENTORY_READ"))));
        when(setPort.findBySetId("legacy")).thenReturn(Optional.of(preset(15L, "legacy-compat",
                new RoleRef("wms", "INVENTORY_READ"))));
        when(setPort.findBySetId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.grant(grant("acme", "op-target", "g1")))
                .isInstanceOf(DomainPermissionSetNotFoundException.class);
        assertThatThrownBy(() -> useCase.grant(grant("acme", "op-target", "legacy")))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.grant(grant("acme", "op-target", "missing")))
                .isInstanceOf(DomainPermissionSetNotFoundException.class);
        verify(setPort, never()).insertGrant(anyLong(), anyString(), anyLong(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("AC-3: a set covering an unsubscribed domain → 422 DOMAIN_NOT_SUBSCRIBED, nothing written")
    void grant_unsubscribed_422() {
        gate(ActionCode.OPERATOR_DOMAIN_SET_GRANT, "acme", "acme", false);
        member(op(7L, "op-target", "acme"), "acme");
        when(setPort.findBySetId("p-scm")).thenReturn(Optional.of(preset(8L, "scm-full",
                new RoleRef("scm", "SCM_OPERATOR"))));
        doThrow(new DomainNotSubscribedException("scm")).when(entitlementGuard).requireEntitled("acme", List.of("scm"));

        assertThatThrownBy(() -> useCase.grant(grant("acme", "op-target", "p-scm")))
                .isInstanceOf(DomainNotSubscribedException.class);
        verify(setPort, never()).insertGrant(anyLong(), anyString(), anyLong(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("same DIRECT row already there → 409 ALREADY_GRANTED (only the DIRECT source is asked)")
    void grant_alreadyGrantedDirect_409() {
        gate(ActionCode.OPERATOR_DOMAIN_SET_GRANT, "acme", "acme", false);
        member(op(7L, "op-target", "acme"), "acme");
        when(setPort.findBySetId("s1")).thenReturn(Optional.of(tenantSet(11L, "s1", "acme",
                new RoleRef("wms", "INVENTORY_READ"))));
        when(setPort.grantExists(7L, "acme", 11L, "DIRECT")).thenReturn(true);

        assertThatThrownBy(() -> useCase.grant(grant("acme", "op-target", "s1")))
                .isInstanceOf(DomainPermissionSetAlreadyGrantedException.class);
        verify(setPort, never()).grantExists(anyLong(), anyString(), anyLong(), eq("MIGRATION"));
    }

    // ── revoke ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("AC-1 (slice A): personal revoke deletes DIRECT + MIGRATION + ORIGIN (773) only; the GROUP row stays and is counted in the audit")
    void revoke_keepsGroupRows() {
        gate(ActionCode.OPERATOR_DOMAIN_SET_REVOKE, "acme", "acme", false);
        member(op(7L, "op-target", "acme"), "acme");
        SetView set = tenantSet(11L, "s1", "acme", new RoleRef("wms", "INVENTORY_READ"));
        when(setPort.findBySetId("s1")).thenReturn(Optional.of(set));
        when(setPort.deleteGrants(7L, "acme", 11L, List.of("DIRECT", "MIGRATION", "ORIGIN")))
                .thenReturn(List.of("DIRECT"));
        when(setPort.findGrantRows(7L, List.of("acme"))).thenReturn(List.of(
                new GrantRowView(7L, "acme", set, "GROUP:44", 44L, "g-44", "재고팀", "op-x", Instant.EPOCH)));
        when(auditor.newAuditId()).thenReturn("a");

        useCase.revoke(new RevokeDomainPermissionSetCommand(ADMIN, "acme", "op-target", "s1", "r"));

        ArgumentCaptor<AdminActionAuditor.AuditRecord> rec = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).recordWithPermission(rec.capture(), eq(Permission.DOMAIN_ACCESS_MANAGE));
        assertThat(rec.getValue().actionCode()).isEqualTo(ActionCode.OPERATOR_DOMAIN_SET_REVOKE);
        assertThat(rec.getValue().downstreamDetail()).isEqualTo("setId=s1 removedSources=DIRECT keptGroupSources=1");
    }

    @Test
    @DisplayName("🔴 TASK-MONO-773 OD-4: the creator's ORIGIN row is removed by the personal revoke (self included) — audit removedSources=ORIGIN")
    void revoke_removesOrigin() {
        gate(ActionCode.OPERATOR_DOMAIN_SET_REVOKE, "acme", "acme", false);
        member(op(7L, "op-target", "acme"), "acme");
        SetView set = preset(14L, "all-domains-full", new RoleRef("wms", "INVENTORY_READ"));
        when(setPort.findBySetId(set.setId())).thenReturn(Optional.of(set));
        when(setPort.deleteGrants(7L, "acme", 14L, List.of("DIRECT", "MIGRATION", "ORIGIN")))
                .thenReturn(List.of("ORIGIN"));
        when(setPort.findGrantRows(7L, List.of("acme"))).thenReturn(List.of());
        when(auditor.newAuditId()).thenReturn("a");

        useCase.revoke(new RevokeDomainPermissionSetCommand(ADMIN, "acme", "op-target", set.setId(), "r"));

        ArgumentCaptor<AdminActionAuditor.AuditRecord> rec = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).recordWithPermission(rec.capture(), eq(Permission.DOMAIN_ACCESS_MANAGE));
        assertThat(rec.getValue().downstreamDetail())
                .isEqualTo("setId=" + set.setId() + " removedSources=ORIGIN keptGroupSources=0");
    }

    @Test
    @DisplayName("revoke with only a GROUP row (nothing DIRECT / MIGRATION / ORIGIN) → 404, no audit row")
    void revoke_onlyGroupRow_404() {
        gate(ActionCode.OPERATOR_DOMAIN_SET_REVOKE, "acme", "acme", false);
        member(op(7L, "op-target", "acme"), "acme");
        when(setPort.findBySetId("s1")).thenReturn(Optional.of(tenantSet(11L, "s1", "acme",
                new RoleRef("wms", "INVENTORY_READ"))));
        when(setPort.deleteGrants(7L, "acme", 11L, List.of("DIRECT", "MIGRATION", "ORIGIN"))).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.revoke(new RevokeDomainPermissionSetCommand(ADMIN, "acme", "op-target",
                "s1", "r"))).isInstanceOf(DomainPermissionSetNotFoundException.class);
        verify(auditor, never()).recordWithPermission(any(), any());
    }

    // ── reads ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("domain-access: roles = union in catalog order; one set with two sources (MIGRATION + GROUP) listed once")
    void domainAccess_unionAndSources() {
        when(tenantGate.resolveWithoutDenyRow(ADMIN, "acme", Permission.DOMAIN_ACCESS_MANAGE))
                .thenReturn(new QueryTenantScopeGate.Resolved("acme", false));
        member(op(7L, "op-target", "acme"), "acme");
        SetView outbound = tenantSet(11L, "s1", "acme", new RoleRef("wms", "OUTBOUND_READ"));
        SetView inventory = preset(2L, "wms-inventory-operate",
                new RoleRef("wms", "INVENTORY_READ"), new RoleRef("wms", "INVENTORY_WRITE"));
        when(setPort.findGrantRows(7L, List.of("acme"))).thenReturn(List.of(
                new GrantRowView(7L, "acme", outbound, "MIGRATION", null, null, null, null, Instant.parse("2026-10-01T00:00:00Z")),
                new GrantRowView(7L, "acme", outbound, "GROUP:44", 44L, "g-44", "출고팀", "op-x", Instant.parse("2026-10-02T00:00:00Z")),
                new GrantRowView(7L, "acme", inventory, "DIRECT", null, null, null, "op-admin", Instant.parse("2026-10-03T00:00:00Z"))));

        OperatorDomainAccessResult r = useCase.domainAccess(ADMIN, "acme", "op-target");

        assertThat(r.roles()).containsExactly(new RoleRef("wms", "INVENTORY_READ"),
                new RoleRef("wms", "INVENTORY_WRITE"), new RoleRef("wms", "OUTBOUND_READ"));
        assertThat(r.sets()).extracting(OperatorDomainAccessResult.SetAccess::setId).containsExactly("s1",
                "preset-wms-inventory-operate");
        assertThat(r.sets().get(0).sources()).extracting(OperatorDomainAccessResult.Source::source)
                .containsExactly("MIGRATION", "GROUP");
        assertThat(r.sets().get(0).sources().get(1).groupId()).isEqualTo("g-44");
        assertThat(r.sets().get(0).sources().get(0).grantedBy()).isNull();
    }

    @Test
    @DisplayName("domain-access of a '*' target: needs the '*' scope (403 otherwise); reads T + '*' rows")
    void domainAccess_starTarget() {
        when(tenantGate.resolveWithoutDenyRow(ADMIN, "acme", Permission.DOMAIN_ACCESS_MANAGE))
                .thenReturn(new QueryTenantScopeGate.Resolved("acme", false));
        when(tenantGate.resolveWithoutDenyRow(ADMIN, null, Permission.DOMAIN_ACCESS_MANAGE))
                .thenReturn(new QueryTenantScopeGate.Resolved("*", true));
        when(tenantGate.resolveWithoutDenyRow(ADMIN, "globex", Permission.DOMAIN_ACCESS_MANAGE))
                .thenReturn(new QueryTenantScopeGate.Resolved("globex", true));
        when(operatorPort.findByOperatorId("op-platform")).thenReturn(Optional.of(op(9L, "op-platform", "*")));
        when(setPort.findGrantRows(9L, List.of("*"))).thenReturn(List.of());
        when(setPort.findGrantRows(9L, List.of("globex", "*"))).thenReturn(List.of());

        assertThatThrownBy(() -> useCase.domainAccess(ADMIN, "acme", "op-platform"))
                .isInstanceOf(TenantScopeDeniedException.class);
        assertThat(useCase.domainAccess(ADMIN, null, "op-platform").tenantId()).isEqualTo("*");
        assertThat(useCase.domainAccess(ADMIN, "globex", "op-platform").roles()).isEmpty();
    }

    @Test
    @DisplayName("me: a tenant I am not a member of → 200 empty (nothing disclosed, nothing read)")
    void me_nonMember_empty() {
        AdminOperatorPort.OperatorView self = op(2L, "op-admin", "acme");
        member(self, "acme");

        OperatorDomainAccessResult r = useCase.myDomainAccess(ADMIN, "globex");

        assertThat(r.roles()).isEmpty();
        assertThat(r.sets()).isEmpty();
        verify(setPort, never()).findGrantRows(anyLong(), any());
    }
}
