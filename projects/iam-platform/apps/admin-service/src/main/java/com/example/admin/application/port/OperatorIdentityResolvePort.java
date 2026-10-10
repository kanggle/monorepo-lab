package com.example.admin.application.port;

/**
 * TASK-MONO-772 S3 — the born-unified central identity of a newly created operator (ADR-MONO-034 U3 · ADR-036),
 * resolved or minted at account-service ({@code POST /internal/tenants/{tenantId}/identities:resolveOrCreate},
 * {@code reuseExisting=true} — the operator converges on the identity the accepting pool account already has).
 *
 * <p><b>Fail-soft</b>: returns {@code null} on any failure — the operator is then born unlinked (a later
 * {@code identity:link} reconciles it). The same stance as {@code FirstAdminProvisioner}; the acceptance calls it
 * after its transaction has committed (auth-to-admin.md § accept).
 */
public interface OperatorIdentityResolvePort {

    /** @return the identity id, or {@code null} when it could not be resolved (never throws). */
    String resolveOrCreateIdentity(String tenantId, String email);
}
