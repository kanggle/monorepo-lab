package com.example.admin.application;

import com.example.admin.application.exception.OperatorOwnedTenantLimitExceededException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.DomainPermissionSetPort;
import com.example.admin.application.port.OperatorRowLockPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.domain.rbac.DomainGrantSource;
import com.example.admin.infrastructure.client.AccountServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-474 (ADR-MONO-044 D2/D5) — the privilege-origination confinement proof.
 * The whole safety story is: every {@code tenant_id} the provisioner writes equals
 * the just-created tenant, never {@code '*'} / an existing tenant. These tests assert
 * that structurally, with mocked ports (no DB / infra). TASK-MONO-773 S3 adds the creator's ORIGIN set row (both
 * branches) and the cap re-count under the operator-row lock ({@link FirstAdminProvisioner#addMembership}).
 */
class FirstAdminProvisionerTest {

    private static final String NEW_TENANT = "acme-corp";
    private static final long ALL_DOMAINS_FULL_ID = 14L;

    private final AdminOperatorPort operatorPort = mock(AdminOperatorPort.class);
    private final OperatorTenantAssignmentPort assignmentPort = mock(OperatorTenantAssignmentPort.class);
    private final AccountServiceClient accountServiceClient = mock(AccountServiceClient.class);
    private final DomainPermissionSetPort setPort = mock(DomainPermissionSetPort.class);
    private final OperatorRowLockPort lockPort = mock(OperatorRowLockPort.class);
    private final FirstAdminProvisioner provisioner = new FirstAdminProvisioner(operatorPort, assignmentPort,
            accountServiceClient, new OperatorMembershipAddition(operatorPort, assignmentPort, null), setPort,
            new OperatorOwnedTenantLimit(operatorPort, 3), lockPort);

    FirstAdminProvisionerTest() {
        when(setPort.findPresets()).thenReturn(List.of(
                preset(2L, "wms-full"), preset(ALL_DOMAINS_FULL_ID, "all-domains-full"), preset(15L, "legacy-compat")));
    }

    private static DomainPermissionSetPort.SetView preset(long id, String key) {
        return new DomainPermissionSetPort.SetView(id, "preset-" + key, null, key, key, null, List.of(),
                Instant.EPOCH, Instant.EPOCH);
    }

    private AdminOperatorPort.OperatorView stubCreatedOperator(long internalId) {
        return new AdminOperatorPort.OperatorView(
                internalId, "op-uuid", NEW_TENANT, "owner@acme.com", null, "Owner",
                "ACTIVE", null, null, Instant.now(), Instant.now(), null, null);
    }

    private Map<String, AdminOperatorPort.RoleView> stubRoles() {
        Map<String, AdminOperatorPort.RoleView> roles = new LinkedHashMap<>();
        roles.put("TENANT_ADMIN", new AdminOperatorPort.RoleView(1L, "TENANT_ADMIN", "", false));
        roles.put("TENANT_BILLING_ADMIN", new AdminOperatorPort.RoleView(2L, "TENANT_BILLING_ADMIN", "", false));
        return roles;
    }

    @Test
    @DisplayName("D2: every written tenant_id is the new tenant — operator home, both role rows, assignment, and (773) the ORIGIN set row")
    void confinesEveryWriteToTheNewTenant() {
        when(operatorPort.existsByTenantIdAndEmail(eq(NEW_TENANT), any())).thenReturn(false);
        when(operatorPort.createOperator(any())).thenReturn(stubCreatedOperator(42L));
        when(operatorPort.resolveRolesByName(FirstAdminProvisioner.FIRST_ADMIN_ROLES)).thenReturn(stubRoles());
        when(accountServiceClient.resolveOrCreateIdentity(eq(NEW_TENANT), any(), eq(true))).thenReturn("identity-1");

        provisioner.provision(NEW_TENANT, "acc-owner-1", "Owner@Acme.com", "Owner");

        // fix-001: the operator's oidc_subject is set to the caller's account_id so they can
        // token-exchange into the console as this operator.
        verify(operatorPort).updateOidcSubject(eq(42L), eq("acc-owner-1"), any());

        // operator home tenant = new tenant, email normalized, OIDC-only (null password)
        ArgumentCaptor<AdminOperatorPort.NewOperator> op = ArgumentCaptor.forClass(AdminOperatorPort.NewOperator.class);
        verify(operatorPort).createOperator(op.capture());
        assertThat(op.getValue().tenantId()).isEqualTo(NEW_TENANT);
        assertThat(op.getValue().email()).isEqualTo("owner@acme.com");
        assertThat(op.getValue().passwordHash()).isNull();
        assertThat(op.getValue().status()).isEqualTo("ACTIVE");

        // BOTH role rows scoped to the new tenant (never '*'), granted_by = null (system)
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AdminOperatorPort.NewRoleBinding>> roles =
                ArgumentCaptor.forClass(List.class);
        verify(operatorPort).saveOperatorRoles(roles.capture());
        assertThat(roles.getValue()).hasSize(2);
        assertThat(roles.getValue()).allSatisfy(b -> {
            assertThat(b.tenantId()).isEqualTo(NEW_TENANT);
            assertThat(b.tenantId()).isNotEqualTo("*");
            assertThat(b.operatorInternalId()).isEqualTo(42L);
            assertThat(b.grantedBy()).isNull();
        });
        assertThat(roles.getValue()).extracting(AdminOperatorPort.NewRoleBinding::roleId)
                .containsExactlyInAnyOrder(1L, 2L);

        // identity linked (non-null), assignment on the new tenant
        verify(operatorPort).linkIdentity(eq(42L), eq("identity-1"), any());
        verify(assignmentPort).createAssignment(eq(42L), eq(NEW_TENANT), eq(null));

        // 🔴 TASK-MONO-773 OD-4 — exactly one ORIGIN row: (new operator, new tenant, all-domains-full, no granter)
        verify(setPort).insertGrant(eq(42L), eq(NEW_TENANT), eq(ALL_DOMAINS_FULL_ID), eq(DomainGrantSource.ORIGIN),
                isNull(), isNull(), any());
        verify(setPort, org.mockito.Mockito.times(1))
                .insertGrant(anyLong(), anyString(), anyLong(), anyString(), any(), any(), any());
        // a new row has nothing to race on — no lock, no re-count
        verify(lockPort, never()).lockForUpdate(anyLong());
    }

    @Test
    @DisplayName("D5: identity resolve-or-create is called with reuseExisting=true; unlinked when identity is null (fail-soft)")
    void bornUnifiedReuseAndFailSoft() {
        when(operatorPort.existsByTenantIdAndEmail(any(), any())).thenReturn(false);
        when(operatorPort.createOperator(any())).thenReturn(stubCreatedOperator(7L));
        when(operatorPort.resolveRolesByName(any())).thenReturn(stubRoles());
        when(accountServiceClient.resolveOrCreateIdentity(eq(NEW_TENANT), any(), eq(true))).thenReturn(null);

        provisioner.provision(NEW_TENANT, "acc-owner-1", "owner@acme.com", "Owner");

        verify(accountServiceClient).resolveOrCreateIdentity(eq(NEW_TENANT), eq("owner@acme.com"), eq(true));
        // null identity → operator born unlinked (no linkIdentity), but assignment still created
        verify(operatorPort, never()).linkIdentity(anyLong(), any(), any());
        verify(assignmentPort).createAssignment(eq(7L), eq(NEW_TENANT), eq(null));
    }

    @Test
    @DisplayName("🔴 773 OD-4: the all-domains-full preset missing (a broken V0052) → the transaction fails — never a creator without the row")
    void missingPreset_failsClosed() {
        when(setPort.findPresets()).thenReturn(List.of(preset(2L, "wms-full")));
        when(operatorPort.existsByTenantIdAndEmail(any(), any())).thenReturn(false);
        when(operatorPort.createOperator(any())).thenReturn(stubCreatedOperator(7L));
        when(operatorPort.resolveRolesByName(any())).thenReturn(stubRoles());

        assertThatThrownBy(() -> provisioner.provision(NEW_TENANT, "acc-owner-1", "owner@acme.com", "Owner"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("all-domains-full");
        verify(setPort, never()).insertGrant(anyLong(), anyString(), anyLong(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("🔴 TASK-MONO-784 S5 + 773 S3 addMembership: 행 잠금 → 상한 재셈 → 새 테넌트 역할 둘 + 배정 + ORIGIN — 새 운영자 행 · oidc_subject · 신원 링크 쓰기 0 · 다른 회사 행 바이트 불변")
    void addMembership_writesOnlyTheNewTenantRows() {
        InMemoryRoleRowsPort operators = new InMemoryRoleRowsPort()
                .role(1, "TENANT_ADMIN").role(2, "TENANT_BILLING_ADMIN").role(3, "SUPPORT_LOCK");
        AdminOperatorPort.OperatorView existing = new AdminOperatorPort.OperatorView(
                41L, "op-41", "home-co", "owner@acme.com", null, "Owner", "ACTIVE",
                null, null, Instant.EPOCH, Instant.EPOCH, null, "identity-41");
        operators.operator(existing)
                .row(41L, "TENANT_ADMIN", "home-co", null, null)
                .row(41L, "SUPPORT_LOCK", "other-co", null, null);
        InMemoryAssignmentPort assignments = new InMemoryAssignmentPort().allowCreate()
                .row(41L, "home-co", null, null);
        List<InMemoryRoleRowsPort.Row> before = List.copyOf(operators.rows);
        FirstAdminProvisioner real = new FirstAdminProvisioner(operators, assignments, accountServiceClient,
                new OperatorMembershipAddition(operators, assignments, null), setPort,
                new OperatorOwnedTenantLimit(operators, 3), lockPort);

        FirstAdminProvisioner.Result result = real.addMembership(existing, NEW_TENANT);

        assertThat(result.operatorId()).isEqualTo("op-41");
        assertThat(result.operatorInternalId()).isEqualTo(41L);
        assertThat(operators.rowsIn(41L, NEW_TENANT)).extracting(InMemoryRoleRowsPort.Row::roleName)
                .containsExactlyInAnyOrder("TENANT_ADMIN", "TENANT_BILLING_ADMIN");
        assertThat(operators.rowsOutside(41L, NEW_TENANT)).containsExactlyElementsOf(before);
        assertThat(assignments.find(41L, NEW_TENANT)).hasValueSatisfying(r -> assertThat(r.grantedBy()).isNull());
        assertThat(assignments.find(41L, "home-co")).isPresent();
        // born-unified is for a NEW row only (the fake operator port would throw on create / oidc / link writes)
        org.mockito.Mockito.verifyNoInteractions(accountServiceClient);
        // 🔴 773: the lock is the first thing; one ORIGIN row in the new tenant only
        InOrder order = inOrder(lockPort, setPort);
        order.verify(lockPort).lockForUpdate(41L);
        order.verify(setPort).insertGrant(eq(41L), eq(NEW_TENANT), eq(ALL_DOMAINS_FULL_ID),
                eq(DomainGrantSource.ORIGIN), isNull(), isNull(), any());
        verify(setPort, org.mockito.Mockito.times(1))
                .insertGrant(anyLong(), anyString(), anyLong(), anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("🔴 773 OD-3: 잠금 뒤 재셈이 상한(경쟁 요청이 먼저 커밋) → 422 · 역할 · 배정 · ORIGIN 쓰기 0")
    void addMembership_capRecountedUnderTheLock() {
        InMemoryRoleRowsPort operators = new InMemoryRoleRowsPort()
                .role(1, "TENANT_ADMIN").role(2, "TENANT_BILLING_ADMIN");
        AdminOperatorPort.OperatorView existing = new AdminOperatorPort.OperatorView(
                41L, "op-41", "home-co", "owner@acme.com", null, "Owner", "ACTIVE",
                null, null, Instant.EPOCH, Instant.EPOCH, null, "identity-41");
        operators.operator(existing)
                .row(41L, "TENANT_ADMIN", "home-co", null, null)
                .row(41L, "TENANT_ADMIN", "co-2", null, null);
        InMemoryAssignmentPort assignments = new InMemoryAssignmentPort().allowCreate();
        org.mockito.Mockito.doAnswer(inv -> {
            operators.row(41L, "TENANT_ADMIN", "co-raced", null, null); // committed while we waited
            return null;
        }).when(lockPort).lockForUpdate(41L);
        FirstAdminProvisioner real = new FirstAdminProvisioner(operators, assignments, accountServiceClient,
                new OperatorMembershipAddition(operators, assignments, null), setPort,
                new OperatorOwnedTenantLimit(operators, 3), lockPort);

        assertThatThrownBy(() -> real.addMembership(existing, NEW_TENANT))
                .isInstanceOf(OperatorOwnedTenantLimitExceededException.class);
        assertThat(operators.rowsIn(41L, NEW_TENANT)).isEmpty();
        assertThat(assignments.find(41L, NEW_TENANT)).isEmpty();
        verify(setPort, never()).insertGrant(anyLong(), anyString(), anyLong(), anyString(), any(), any(), any());
    }
}
