package com.example.admin.application;

import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.infrastructure.client.AccountServiceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-474 (ADR-MONO-044 D2/D5) — the privilege-origination confinement proof.
 * The whole safety story is: every {@code tenant_id} the provisioner writes equals
 * the just-created tenant, never {@code '*'} / an existing tenant. These tests assert
 * that structurally, with mocked ports (no DB / infra).
 */
class FirstAdminProvisionerTest {

    private static final String NEW_TENANT = "acme-corp";

    private final AdminOperatorPort operatorPort = mock(AdminOperatorPort.class);
    private final OperatorTenantAssignmentPort assignmentPort = mock(OperatorTenantAssignmentPort.class);
    private final AccountServiceClient accountServiceClient = mock(AccountServiceClient.class);
    private final FirstAdminProvisioner provisioner =
            new FirstAdminProvisioner(operatorPort, assignmentPort, accountServiceClient);

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
    @DisplayName("D2: every written tenant_id is the new tenant — operator home, both role rows, and assignment")
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
}
