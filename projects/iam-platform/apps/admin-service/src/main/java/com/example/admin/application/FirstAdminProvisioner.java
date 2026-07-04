package com.example.admin.application;

import com.example.admin.application.exception.OperatorEmailConflictException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.OperatorTenantAssignmentPort;
import com.example.admin.infrastructure.client.AccountServiceClient;
import com.example.common.id.UuidV7;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * TASK-BE-474 (ADR-MONO-044 D1/D2/D5) — the atomic admin-service-local half of
 * self-service tenant onboarding: mints the FIRST operator for a brand-new tenant
 * and grants it {@code TENANT_ADMIN} + {@code TENANT_BILLING_ADMIN} scoped to that
 * tenant, plus a whole-tenant assignment.
 *
 * <p><b>Privilege-origination trust anchor (ADR-044 D2 — the safety keystone).</b>
 * Unlike {@link CreateOperatorUseCase}, this path has NO operator actor (the caller
 * is an ordinary authenticated visitor, not an operator), so it does NOT run
 * {@code TenantScopeGuard} / {@code RoleGrantGuard} — there is no scope to confine
 * against. What keeps it safe is structural, not a guard: <b>every {@code tenant_id}
 * written here is {@code newTenantId}</b> — the id of the tenant the orchestrator
 * just created in the same request. The role rows can only ever point at that fresh,
 * empty, isolated tenant; they can never target {@code '*'} or an existing tenant.
 * That confinement is the entire reason self-elevation is acceptable (GCP "you own
 * the project you created, nothing else"). {@code SUPER_ADMIN} stays net-zero.
 *
 * <p>All writes run in ONE {@link Transactional} so a failure rolls back the operator,
 * its role bindings, and the assignment together (no half-provisioned admin). The
 * cross-service tenant row is compensated by the orchestrator
 * ({@link SelfServiceOnboardingUseCase}) since it is not part of this DB transaction.
 *
 * <p>The identity link (born-unified, ADR-036) is <b>fail-soft</b> with
 * {@code reuseExisting=true} — if the visitor already has a central identity (e.g. a
 * prior consumer {@code /signup}), the operator converges on it (ADR-032 one Global
 * Account, two facets); on identity-infra failure the operator is born unlinked
 * (reconciled later), exactly like {@link CreateOperatorUseCase} step 3d.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FirstAdminProvisioner {

    static final String STATUS_ACTIVE = "ACTIVE";
    /** ADR-044 D6: the first admin gets BOTH — administer AND self-enable domain subscriptions. */
    static final List<String> FIRST_ADMIN_ROLES = List.of("TENANT_ADMIN", "TENANT_BILLING_ADMIN");

    private final AdminOperatorPort operatorPort;
    private final OperatorTenantAssignmentPort assignmentPort;
    private final AccountServiceClient accountServiceClient;

    /**
     * @param newTenantId     the just-created tenant — the SOLE tenant_id every write below targets (D2)
     * @param callerAccountId the caller's OIDC subject (auth-service account_id) — set as the operator's
     *                        {@code oidc_subject} so the owner can immediately token-exchange into the
     *                        console as this operator (fix-001: without it the operator is unreachable)
     * @param email           the caller's email (from the authoritative account, not user input)
     * @param displayName     the operator display name (caller's account display name; never blank)
     * @return the minted operator's external UUID + internal id
     */
    @Transactional
    public Result provision(String newTenantId, String callerAccountId, String email, String displayName) {
        String normalizedEmail = email == null ? null : email.trim().toLowerCase();
        Instant now = Instant.now();

        // A re-onboard collision (same email already an operator in this fresh tenant) is
        // effectively impossible for a brand-new tenant, but guard it to match the
        // (tenant_id, email) composite unique index (V0025) rather than surface a raw DB error.
        if (normalizedEmail != null && operatorPort.existsByTenantIdAndEmail(newTenantId, normalizedEmail)) {
            throw new OperatorEmailConflictException("Operator email already exists in tenant " + newTenantId);
        }

        String operatorUuid = UuidV7.randomString();
        // OIDC-only operator (password_hash = NULL, ADR-035 O2): the owner's primary login is
        // their unified IAM OIDC credential via the ADR-014 token-exchange — no break-glass password.
        AdminOperatorPort.OperatorView created = operatorPort.createOperator(
                new AdminOperatorPort.NewOperator(
                        operatorUuid, newTenantId, normalizedEmail, null,
                        displayName, STATUS_ACTIVE, now));

        // fix-001: bind the operator's oidc_subject = the caller's account_id (the OIDC `sub`).
        // This is what lets the owner IMMEDIATELY token-exchange (POST /api/admin/auth/token-exchange)
        // their unified IAM token into an operator token for the new tenant — i.e. actually log into
        // the console as this operator. Without it the operator exists but is unreachable via OIDC
        // (findByOidcSubject returns empty → 401), the gap live verification caught. oidc_subject is
        // platform-global UNIQUE (V0027); for a first-time onboarder it is free.
        if (callerAccountId != null && !callerAccountId.isBlank()) {
            operatorPort.updateOidcSubject(created.internalId(), callerAccountId, now);
        }

        // D2 CONFINEMENT: role bindings are stamped with newTenantId — never the actor's
        // (there is none), never '*', never a role-supplied value. granted_by = null (system
        // origination — no operator granted this; the orchestrator did, gated by tenant creation).
        Map<String, AdminOperatorPort.RoleView> roles = operatorPort.resolveRolesByName(FIRST_ADMIN_ROLES);
        List<AdminOperatorPort.NewRoleBinding> bindings = roles.values().stream()
                .map(role -> new AdminOperatorPort.NewRoleBinding(
                        created.internalId(), role.id(), now, null, newTenantId))
                .toList();
        operatorPort.saveOperatorRoles(bindings);

        // Born-unified identity (ADR-036) — fail-soft, reuseExisting=true (converge on the
        // visitor's existing central identity if any). null → operator born unlinked.
        String identityId = accountServiceClient.resolveOrCreateIdentity(newTenantId, normalizedEmail, true);
        if (identityId != null) {
            operatorPort.linkIdentity(created.internalId(), identityId, now);
        }

        // Whole-tenant assignment so the owner can assume-tenant into their new tenant.
        // granted_by = null (system origination).
        assignmentPort.createAssignment(created.internalId(), newTenantId, null);

        log.info("self-service onboarding: minted first admin operator={} tenant={} roles={}",
                operatorUuid, newTenantId, FIRST_ADMIN_ROLES);
        return new Result(operatorUuid, created.internalId());
    }

    /** The minted operator's external UUID + internal BIGINT id. */
    public record Result(String operatorId, long operatorInternalId) {}
}
