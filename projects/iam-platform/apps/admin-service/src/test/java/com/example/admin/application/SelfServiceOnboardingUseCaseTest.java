package com.example.admin.application;

import com.example.admin.application.port.TenantProvisioningPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-474 (ADR-MONO-044 D1/D3) — orchestration + fail-closed compensation.
 */
class SelfServiceOnboardingUseCaseTest {

    private static final String TENANT = "acme-corp";

    private final TenantProvisioningPort tenantPort = mock(TenantProvisioningPort.class);
    private final FirstAdminProvisioner provisioner = mock(FirstAdminProvisioner.class);
    private final SelfServiceOnboardingUseCase useCase =
            new SelfServiceOnboardingUseCase(tenantPort, provisioner);

    @Test
    @DisplayName("D1: creates the tenant (B2B) then mints the first admin; no compensation on success")
    void happyPath() {
        when(provisioner.provision(eq(TENANT), any(), any(), any()))
                .thenReturn(new FirstAdminProvisioner.Result("op-uuid", 42L));

        SelfServiceOnboardingUseCase.Result result =
                useCase.onboard(TENANT, "Acme Corp", "acc-1", "owner@acme.com", "Owner");

        verify(tenantPort).create(eq(TENANT), eq("Acme Corp"), eq("B2B_ENTERPRISE"));
        verify(provisioner).provision(eq(TENANT), eq("acc-1"), eq("owner@acme.com"), eq("Owner"));
        // no compensation
        verify(tenantPort, never()).update(any(), any(), any());
        assertThat(result.tenantId()).isEqualTo(TENANT);
        assertThat(result.operatorId()).isEqualTo("op-uuid");
        assertThat(result.roles()).containsExactly("TENANT_ADMIN", "TENANT_BILLING_ADMIN");
    }

    @Test
    @DisplayName("D3: first-admin provisioning failure compensates the orphan tenant (SUSPEND) and rethrows")
    void compensatesOnProvisioningFailure() {
        RuntimeException boom = new IllegalStateException("mint failed");
        when(provisioner.provision(eq(TENANT), any(), any(), any())).thenThrow(boom);

        assertThatThrownBy(() -> useCase.onboard(TENANT, "Acme Corp", "acc-1", "owner@acme.com", "Owner"))
                .isSameAs(boom);

        // tenant was created, then compensated by SUSPEND — no orphan ACTIVE tenant
        verify(tenantPort).create(eq(TENANT), any(), eq("B2B_ENTERPRISE"));
        verify(tenantPort).update(eq(TENANT), isNull(), eq("SUSPENDED"));
    }

    @Test
    @DisplayName("D3: a compensation failure does not mask the original provisioning error")
    void compensationFailureDoesNotMaskOriginal() {
        RuntimeException boom = new IllegalStateException("mint failed");
        when(provisioner.provision(eq(TENANT), any(), any(), any())).thenThrow(boom);
        when(tenantPort.update(eq(TENANT), isNull(), eq("SUSPENDED")))
                .thenThrow(new RuntimeException("account-service down"));

        assertThatThrownBy(() -> useCase.onboard(TENANT, "Acme Corp", "acc-1", "owner@acme.com", "Owner"))
                .isSameAs(boom);
    }
}
