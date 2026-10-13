package com.example.admin.application;

import com.example.admin.application.exception.DomainNotSubscribedException;
import com.example.admin.application.exception.DomainPermissionSetAlreadyGrantedException;
import com.example.admin.application.exception.DomainPermissionSetNotFoundException;
import com.example.admin.application.exception.GroupNotFoundException;
import com.example.admin.application.exception.IdempotencyKeyConflictException;
import com.example.admin.application.exception.TenantScopeDeniedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.DomainPermissionSetPort;
import com.example.admin.application.port.DomainPermissionSetPort.GroupSetGrantView;
import com.example.admin.application.port.DomainPermissionSetPort.SetView;
import com.example.admin.domain.rbac.DomainRoleCatalog.RoleRef;
import com.example.admin.domain.rbac.Permission;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupJpaRepository;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupMemberJpaEntity;
import com.example.admin.infrastructure.persistence.rbac.OperatorGroupMemberJpaRepository;
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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-631 slice B (ADR-MONO-083 D4 · D5) — {@link GroupDomainPermissionSetUseCase}: the group tenant is the acting
 * tenant (scope 403 on writes / 404 on the read), which sets a group may carry, the fan-out to CURRENT members (never
 * skipped), the revoke of this group's rows only, and the audit details of the contract. Every refusal writes nothing.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class GroupDomainPermissionSetUseCaseTest {

    private static final OperatorContext ADMIN = new OperatorContext("op-admin", "jti");
    private static final long GROUP_PK = 9L;

    @Mock OperatorGroupJpaRepository operatorGroups;
    @Mock OperatorGroupMemberJpaRepository groupMembers;
    @Mock DomainPermissionSetPort setPort;
    @Mock GroupDomainSetFanOut fanOut;
    @Mock TenantScopeGuard tenantScopeGuard;
    @Mock DomainSetEntitlementGuard entitlementGuard;
    @Mock AdminOperatorPort operatorPort;
    @Mock AdminActionAuditor auditor;

    @InjectMocks GroupDomainPermissionSetUseCase useCase;

    private OperatorGroupJpaEntity group(String tenant) {
        OperatorGroupJpaEntity g = mock(OperatorGroupJpaEntity.class);
        org.mockito.Mockito.lenient().when(g.getId()).thenReturn(GROUP_PK);
        org.mockito.Mockito.lenient().when(g.getTenantId()).thenReturn(tenant);
        org.mockito.Mockito.lenient().when(g.getGroupId()).thenReturn("grp-1");
        when(operatorGroups.findByGroupId("grp-1")).thenReturn(Optional.of(g));
        return g;
    }

    private static SetView tenantSet(long id, String setId, String tenant, RoleRef... roles) {
        return new SetView(id, setId, tenant, null, "set " + setId, null, List.of(roles), Instant.EPOCH, Instant.EPOCH);
    }

    private static SetView preset(long id, String key, RoleRef... roles) {
        return new SetView(id, "preset-" + key, null, key, key, null, List.of(roles), Instant.EPOCH, Instant.EPOCH);
    }

    private static GrantGroupDomainPermissionSetCommand cmd(String setId) {
        return new GrantGroupDomainPermissionSetCommand(ADMIN, "grp-1", setId, "reason", "key-1");
    }

    // ── grant ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("grant: template + one GROUP:<id> row per CURRENT member in the group tenant; audit «setId=… fannedOutRows=N selfGrant=false»")
    void grant_happy_fansOutToCurrentMembers() {
        group("acme");
        when(setPort.findBySetId("s1")).thenReturn(Optional.of(tenantSet(11L, "s1", "acme",
                new RoleRef("wms", "INVENTORY_READ"))));
        when(auditor.isIdempotencyKeyUsed("op-admin", ActionCode.GROUP_DOMAIN_SET_GRANT, "key-1")).thenReturn(false);
        when(setPort.groupGrantExists(GROUP_PK, 11L)).thenReturn(false);
        when(operatorPort.resolveActorInternalId("op-admin")).thenReturn(2L);
        when(groupMembers.findByGroupId(GROUP_PK)).thenReturn(List.of(
                OperatorGroupMemberJpaEntity.create(GROUP_PK, 7L, Instant.EPOCH, null),
                OperatorGroupMemberJpaEntity.create(GROUP_PK, 8L, Instant.EPOCH, null)));
        when(fanOut.fanOutSetToMembers(eq(GROUP_PK), eq("acme"), eq(11L), eq(List.of(7L, 8L)), eq(2L), any()))
                .thenReturn(2);
        when(auditor.newAuditId()).thenReturn("a");

        GroupDomainPermissionSetResult.Granted r = useCase.grant(cmd("s1"));

        assertThat(r.fannedOutRows()).isEqualTo(2);
        assertThat(r.item().setId()).isEqualTo("s1");
        assertThat(r.item().grantedBy()).isEqualTo("op-admin");
        verify(tenantScopeGuard).requireTenantInScope(ADMIN, Permission.DOMAIN_ACCESS_MANAGE, "acme",
                ActionCode.GROUP_DOMAIN_SET_GRANT);
        verify(entitlementGuard).requireEntitled("acme", List.of("wms"));
        verify(setPort).insertGroupGrant(eq(GROUP_PK), eq(11L), eq(2L), any());
        ArgumentCaptor<AdminActionAuditor.AuditRecord> rec = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).recordWithPermission(rec.capture(), eq(Permission.DOMAIN_ACCESS_MANAGE));
        assertThat(rec.getValue().actionCode()).isEqualTo(ActionCode.GROUP_DOMAIN_SET_GRANT);
        assertThat(rec.getValue().targetType()).isEqualTo("GROUP");
        assertThat(rec.getValue().targetId()).isEqualTo("grp-1");
        assertThat(rec.getValue().targetTenantId()).isEqualTo("acme");
        assertThat(rec.getValue().idempotencyKey()).isEqualTo("key-1");
        assertThat(rec.getValue().downstreamDetail()).isEqualTo("setId=s1 fannedOutRows=2 selfGrant=false");
    }

    @Test
    @DisplayName("OD-4: the actor is a member of the group → selfGrant=true in the audit detail")
    void grant_actorIsMember_selfGrant() {
        group("acme");
        when(setPort.findBySetId("p1")).thenReturn(Optional.of(preset(1L, "wms-inventory-read",
                new RoleRef("wms", "INVENTORY_READ"))));
        when(auditor.isIdempotencyKeyUsed("op-admin", ActionCode.GROUP_DOMAIN_SET_GRANT, "key-1")).thenReturn(false);
        when(setPort.groupGrantExists(GROUP_PK, 1L)).thenReturn(false);
        when(operatorPort.resolveActorInternalId("op-admin")).thenReturn(2L);
        when(groupMembers.findByGroupId(GROUP_PK)).thenReturn(List.of(
                OperatorGroupMemberJpaEntity.create(GROUP_PK, 2L, Instant.EPOCH, null)));
        when(fanOut.fanOutSetToMembers(eq(GROUP_PK), eq("acme"), eq(1L), eq(List.of(2L)), eq(2L), any()))
                .thenReturn(1);
        when(auditor.newAuditId()).thenReturn("a");

        useCase.grant(cmd("p1"));

        ArgumentCaptor<AdminActionAuditor.AuditRecord> rec = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).recordWithPermission(rec.capture(), eq(Permission.DOMAIN_ACCESS_MANAGE));
        assertThat(rec.getValue().downstreamDetail()).isEqualTo("setId=preset-wms-inventory-read fannedOutRows=1 selfGrant=true");
    }

    @Test
    @DisplayName("grant refusals write nothing: out of scope 403 · other tenant's set 404 · legacy-compat 400 · unsubscribed 422 · already on the group 409 · re-used key 409")
    void grant_refusals_writeNothing() {
        group("acme");
        // out of the group tenant's grant scope
        doThrow(new TenantScopeDeniedException("no")).when(tenantScopeGuard)
                .requireTenantInScope(ADMIN, Permission.DOMAIN_ACCESS_MANAGE, "acme", ActionCode.GROUP_DOMAIN_SET_GRANT);
        assertThatThrownBy(() -> useCase.grant(cmd("s1"))).isInstanceOf(TenantScopeDeniedException.class);
        org.mockito.Mockito.reset(tenantScopeGuard);

        // another tenant's set
        when(setPort.findBySetId("other")).thenReturn(Optional.of(tenantSet(12L, "other", "globex")));
        assertThatThrownBy(() -> useCase.grant(cmd("other"))).isInstanceOf(DomainPermissionSetNotFoundException.class);

        // legacy-compat
        when(setPort.findBySetId("legacy")).thenReturn(Optional.of(preset(15L, "legacy-compat")));
        assertThatThrownBy(() -> useCase.grant(cmd("legacy"))).isInstanceOf(IllegalArgumentException.class);

        // re-used Idempotency-Key
        when(setPort.findBySetId("s1")).thenReturn(Optional.of(tenantSet(11L, "s1", "acme",
                new RoleRef("scm", "SCM_OPERATOR"))));
        when(auditor.isIdempotencyKeyUsed("op-admin", ActionCode.GROUP_DOMAIN_SET_GRANT, "key-1"))
                .thenReturn(true).thenReturn(false).thenReturn(false);
        assertThatThrownBy(() -> useCase.grant(cmd("s1"))).isInstanceOf(IdempotencyKeyConflictException.class);

        // unsubscribed domain of the GROUP's tenant
        doThrow(new DomainNotSubscribedException("scm")).doNothing()
                .when(entitlementGuard).requireEntitled("acme", List.of("scm"));
        assertThatThrownBy(() -> useCase.grant(cmd("s1"))).isInstanceOf(DomainNotSubscribedException.class);

        // already on the group
        when(setPort.groupGrantExists(GROUP_PK, 11L)).thenReturn(true);
        assertThatThrownBy(() -> useCase.grant(cmd("s1"))).isInstanceOf(DomainPermissionSetAlreadyGrantedException.class);

        verify(setPort, never()).insertGroupGrant(anyLong(), anyLong(), any(), any());
        verify(fanOut, never()).fanOutSetToMembers(anyLong(), anyString(), anyLong(), anyCollection(), any(), any());
        verify(auditor, never()).recordWithPermission(any(), any());
    }

    @Test
    @DisplayName("grant: missing Idempotency-Key / setId → 400 before the group is read; unknown group → 404")
    void grant_shapeAndGroup() {
        assertThatThrownBy(() -> useCase.grant(new GrantGroupDomainPermissionSetCommand(ADMIN, "grp-1", "s1", "r", null)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> useCase.grant(new GrantGroupDomainPermissionSetCommand(ADMIN, "grp-1", " ", "r", "k")))
                .isInstanceOf(IllegalArgumentException.class);
        when(operatorGroups.findByGroupId("nope")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> useCase.grant(new GrantGroupDomainPermissionSetCommand(ADMIN, "nope", "s1", "r", "k")))
                .isInstanceOf(GroupNotFoundException.class);
    }

    // ── revoke ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("revoke: deletes this group's rows of the set + the template; audit «setId=… removedRows=N»")
    void revoke_happy() {
        group("acme");
        when(setPort.findBySetId("s1")).thenReturn(Optional.of(tenantSet(11L, "s1", "acme")));
        when(setPort.groupGrantExists(GROUP_PK, 11L)).thenReturn(true);
        when(setPort.deleteGroupSourceRowsOfSet(GROUP_PK, 11L)).thenReturn(3);
        when(auditor.newAuditId()).thenReturn("a");

        useCase.revoke(ADMIN, "grp-1", "s1", "reason");

        verify(tenantScopeGuard).requireTenantInScope(ADMIN, Permission.DOMAIN_ACCESS_MANAGE, "acme",
                ActionCode.GROUP_DOMAIN_SET_REVOKE);
        verify(setPort).deleteGroupGrant(GROUP_PK, 11L);
        ArgumentCaptor<AdminActionAuditor.AuditRecord> rec = ArgumentCaptor.forClass(AdminActionAuditor.AuditRecord.class);
        verify(auditor).recordWithPermission(rec.capture(), eq(Permission.DOMAIN_ACCESS_MANAGE));
        assertThat(rec.getValue().actionCode()).isEqualTo(ActionCode.GROUP_DOMAIN_SET_REVOKE);
        assertThat(rec.getValue().downstreamDetail()).isEqualTo("setId=s1 removedRows=3");
    }

    @Test
    @DisplayName("revoke: the set is not on this group → 404 DOMAIN_PERMISSION_SET_NOT_FOUND, nothing deleted")
    void revoke_notOnGroup_404() {
        group("acme");
        when(setPort.findBySetId("s1")).thenReturn(Optional.of(tenantSet(11L, "s1", "acme")));
        when(setPort.groupGrantExists(GROUP_PK, 11L)).thenReturn(false);

        assertThatThrownBy(() -> useCase.revoke(ADMIN, "grp-1", "s1", "reason"))
                .isInstanceOf(DomainPermissionSetNotFoundException.class);
        verify(setPort, never()).deleteGroupSourceRowsOfSet(anyLong(), anyLong());
        verify(setPort, never()).deleteGroupGrant(anyLong(), anyLong());
    }

    // ── list ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("list: in scope → the templates; out of the group tenant's scope → 404 GROUP_NOT_FOUND (enumeration-safe)")
    void list_scope() {
        group("acme");
        when(tenantScopeGuard.isTenantInScope(ADMIN, Permission.DOMAIN_ACCESS_MANAGE, "acme"))
                .thenReturn(true).thenReturn(false);
        when(setPort.findGroupGrants(GROUP_PK)).thenReturn(List.of(new GroupSetGrantView(GROUP_PK,
                tenantSet(11L, "s1", "acme"), "op-admin", Instant.parse("2026-10-14T09:00:00Z"))));

        List<GroupDomainPermissionSetResult> items = useCase.list(ADMIN, "grp-1");
        assertThat(items).containsExactly(new GroupDomainPermissionSetResult("s1", "set s1", null,
                Instant.parse("2026-10-14T09:00:00Z"), "op-admin"));

        assertThatThrownBy(() -> useCase.list(ADMIN, "grp-1")).isInstanceOf(GroupNotFoundException.class);
    }
}
