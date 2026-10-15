package com.example.admin.application;

import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.TenantProvisioningPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

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
    /** TASK-MONO-772 S3 (S1-11) — the same OIDC-subject → operator lookup the token exchange uses. */
    private final OperatorOidcSubjectResolver oidcSubjectResolver;
    /** TASK-MONO-784 S5 (D-12) — «can this existing row take a new membership» + the cache eviction after it. */
    private final OperatorMembershipAddition membershipAddition;
    /** TASK-MONO-784 S5 (OD-1) — the per-identity cap on self-created tenants. */
    private final OperatorOwnedTenantLimit ownedTenantLimit;
    /** TASK-MONO-773 S3 (ADR-044 D4 · option A) — the verified-email trust gate, before anything is created. */
    private final OnboardingTrustGate trustGate;

    /**
     * @param tenantId         the requested tenant slug (validated {@code ^[a-z][a-z0-9-]{1,31}$} at the DTO)
     * @param organizationName the tenant display name
     * @param callerAccountId  the caller's OIDC subject (account_id) — set as the operator's
     *                         {@code oidc_subject} so they can token-exchange into the console (fix-001)
     * @param callerAccount    the caller's authoritative account record (email + display name — never user
     *                         input), or empty when account-service has none (TASK-MONO-773 option A — the
     *                         {@code iam}-credential operator; the trust gate decides what that means)
     */
    public Result onboard(String tenantId, String organizationName, String callerAccountId,
                          Optional<OnboardingCallerAccount> callerAccount) {
        // TASK-MONO-784 S5 (onboarding-api.md § 다회사 운영자와 온보딩 — ADR-MONO-082 b · D-12 · OD-1), replacing
        // 772 S3's «any facet → 409» (S1-11). EVERYTHING is decided BEFORE the tenant exists — a refusal leaves
        // nothing to compensate (before 772 the tenant was created first and the oidc_subject UNIQUE then left a
        // SUSPENDED empty tenant behind). First failure answers:
        //   1 no facet                      → a new operator row (unchanged)
        //   2 '*' facet                     → 409 OPERATOR_ALREADY_PROVISIONED (ADR-082 D2)
        //   3 non-ACTIVE facet              → 409 OPERATOR_ALREADY_PROVISIONED
        //   4 ACTIVE company facet at cap   → 422 OPERATOR_OWNED_TENANT_LIMIT_EXCEEDED (OD-1)
        //   5 ACTIVE company facet below it → the tenant, then a membership on THAT row
        // (requireCanReceive also refuses «already a member of this slug» — a fresh slug cannot normally be, but an
        // assignment to an unregistered tenant id can exist, e.g. a seeded one; refusing it is the fail-closed side.)
        Optional<AdminOperatorPort.OperatorView> facet = (callerAccountId == null || callerAccountId.isBlank())
                ? Optional.empty()
                : oidcSubjectResolver.resolve(callerAccountId);

        // 0. 🔴 TASK-MONO-773 S3 — the trust gate (onboarding-api.md «트러스트 게이트 — 두 자격»), before the facet
        //    table and before the tenant: a personal account must have a verified email; a caller with no account
        //    record but an operator facet (the iam-credential operator, option A) is exempt and continues on the
        //    facet path below (cap included); no record and no facet → 403. Refusals create nothing.
        trustGate.require(callerAccountId, callerAccount, facet);

        if (facet.isPresent()) {
            membershipAddition.requireCanReceive(facet.get(), tenantId);
            ownedTenantLimit.requireBelowLimit(facet.get().internalId());
        }

        // D1 step 1: create the tenant (cross-service). A duplicate slug surfaces as
        // TenantAlreadyExistsException (409) from the port — no compensation needed
        // (nothing was created).
        tenantPort.create(tenantId, organizationName, TENANT_TYPE_B2B);

        // D1 step 2: mint the first admin atomically — a new row, or the new tenant's membership on the existing
        // row. On ANY failure, compensate the orphan tenant (D3 fail-closed) and rethrow the original cause; the
        // admin-side writes rolled back in their own transaction, so the existing row keeps nothing.
        FirstAdminProvisioner.Result minted;
        try {
            // No facet ⇒ the gate saw an account record (no record and no facet was refused above).
            minted = facet.isPresent()
                    ? firstAdminProvisioner.addMembership(facet.get(), tenantId)
                    : firstAdminProvisioner.provision(tenantId, callerAccountId,
                            callerAccount.orElseThrow().email(), callerAccount.orElseThrow().displayName());
        } catch (RuntimeException provisioningFailure) {
            compensateOrphanTenant(tenantId, provisioningFailure);
            throw provisioningFailure;
        }
        if (facet.isPresent()) {
            // D-5 — the existing row gained permissions; after commit.
            membershipAddition.invalidatePermissionCache(minted.operatorId());
        }
        return new Result(tenantId, minted.operatorId(), FirstAdminProvisioner.FIRST_ADMIN_ROLES);
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
