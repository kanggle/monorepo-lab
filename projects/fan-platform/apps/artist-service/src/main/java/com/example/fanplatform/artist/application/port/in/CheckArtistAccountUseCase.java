package com.example.fanplatform.artist.application.port.in;

/**
 * Inbound port behind {@code GET /internal/artists/exists} — does this account
 * author as an artist in this tenant? (TASK-FAN-BE-045 AC-6, ADR-004 A.)
 *
 * <p>Takes no {@code ActorContext}: the caller is a workload identity, not an
 * end-user actor, and the tenant is an explicit parameter rather than a claim
 * (the {@code client_credentials} chain does not pin {@code tenant_id}).
 */
public interface CheckArtistAccountUseCase {

    /**
     * True iff some artist row in {@code tenantId} has {@code account_id = accountId}.
     *
     * <p><strong>Fail-closed.</strong> An infrastructure failure answers
     * {@code false}, never {@code true} — a validation that opens on error is
     * indistinguishable from having no validation (ADR-004 § Drivers 3).
     * Artist {@code status} is deliberately not consulted; see the contract.
     */
    boolean isArtistAccount(String accountId, String tenantId);
}
