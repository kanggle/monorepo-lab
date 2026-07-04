package com.example.admin.application;

import com.example.admin.application.port.TenantProvisioningPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * TASK-BE-474 (ADR-MONO-044 D1/D3/D7) — orchestrates self-service B2B tenant
 * onboarding: an authenticated visitor creates a NEW tenant and is appointed its
 * first {@code TENANT_ADMIN} + {@code TENANT_BILLING_ADMIN}, with no platform
 * {@code SUPER_ADMIN} in the loop (AWS "create account → root" / GCP "create
 * project → owner" parity).
 *
 * <p>Sequence (D1): (1) create the tenant in account-service, then (2) mint the
 * first admin atomically in admin-service via {@link FirstAdminProvisioner}.
 *
 * <p><b>Fail-closed with compensation (ADR-044 D3 — opposite of ADR-042 seller
 * fail-soft, by design).</b> A tenant with no administrator is a dead, unreachable
 * boundary — worse than no tenant. So if the first-admin provisioning fails after
 * the tenant was created, the orchestrator <b>compensates the orphan tenant</b> by
 * suspending it (there is no tenant hard-delete endpoint; {@code SUSPENDED} freezes
 * logins/signups so the boundary is never usable admin-less) and rethrows — the
 * caller sees an error and no half-provisioned ACTIVE tenant lingers. The
 * admin-service-local writes (operator/roles/assignment) roll back within the
 * provisioner's own transaction; only the cross-service tenant needs compensation.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SelfServiceOnboardingUseCase {

    /** ADR-044: self-service onboarding creates B2B tenants (the org/workspace axis). */
    static final String TENANT_TYPE_B2B = "B2B_ENTERPRISE";
    private static final String SUSPENDED = "SUSPENDED";

    private final TenantProvisioningPort tenantPort;
    private final FirstAdminProvisioner firstAdminProvisioner;

    /**
     * @param tenantId         the requested tenant slug (validated {@code ^[a-z][a-z0-9-]{1,31}$} at the DTO)
     * @param organizationName the tenant display name
     * @param callerAccountId  the caller's OIDC subject (account_id) — set as the operator's
     *                         {@code oidc_subject} so they can token-exchange into the console (fix-001)
     * @param callerEmail      the authenticated caller's email (from the authoritative account, not user input)
     * @param callerDisplayName the operator display name (never blank — controller defaults to email)
     */
    public Result onboard(String tenantId, String organizationName, String callerAccountId,
                          String callerEmail, String callerDisplayName) {
        // D1 step 1: create the tenant (cross-service). A duplicate slug surfaces as
        // TenantAlreadyExistsException (409) from the port — no compensation needed
        // (nothing was created).
        tenantPort.create(tenantId, organizationName, TENANT_TYPE_B2B);

        // D1 step 2: mint the first admin atomically. On ANY failure, compensate the
        // orphan tenant (D3 fail-closed) and rethrow the original cause.
        try {
            FirstAdminProvisioner.Result minted =
                    firstAdminProvisioner.provision(tenantId, callerAccountId, callerEmail, callerDisplayName);
            return new Result(tenantId, minted.operatorId(), FirstAdminProvisioner.FIRST_ADMIN_ROLES);
        } catch (RuntimeException provisioningFailure) {
            compensateOrphanTenant(tenantId, provisioningFailure);
            throw provisioningFailure;
        }
    }

    /**
     * D3 compensation — best-effort suspend of the just-created-but-adminless tenant.
     * A compensation failure must not mask the original provisioning error, so it is
     * logged (not thrown); the tenant may linger ACTIVE-but-adminless in that rare
     * double-failure case and is swept by ops (recorded honestly rather than hidden).
     */
    private void compensateOrphanTenant(String tenantId, RuntimeException cause) {
        try {
            tenantPort.update(tenantId, null, SUSPENDED);
            log.warn("self-service onboarding: first-admin provisioning failed for tenant={}, "
                    + "compensated by SUSPEND (no orphan ACTIVE tenant). cause={}", tenantId, cause.toString());
        } catch (RuntimeException compensationFailure) {
            log.error("self-service onboarding: COMPENSATION FAILED for tenant={} — tenant may linger "
                    + "ACTIVE-but-adminless (ops sweep required). provisioningCause={} compensationCause={}",
                    tenantId, cause.toString(), compensationFailure.toString(), compensationFailure);
        }
    }

    /** The created tenant + minted first-admin operator + its granted roles. */
    public record Result(String tenantId, String operatorId, List<String> roles) {}
}
