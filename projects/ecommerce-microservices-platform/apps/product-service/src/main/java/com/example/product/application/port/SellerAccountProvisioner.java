package com.example.product.application.port;

/**
 * Outbound port for provisioning + deactivating a seller's backing IAM seller-operator
 * account (ADR-MONO-042 D2/D4/D5). The application layer depends on THIS interface; the
 * infrastructure adapter (account-service RestClient over client_credentials JWT)
 * implements it — the application never imports the HTTP client directly (layer rule).
 *
 * <p>All operations are <b>fail-soft</b> (D3): a failure to reach IAM never throws to the
 * caller — provisioning returns an unsuccessful {@link ProvisioningResult} (the seller
 * stays PENDING_PROVISIONING, retryable), and deactivation logs + returns quietly (the
 * seller's domain transition still applies; net-zero for null-account sellers).
 */
public interface SellerAccountProvisioner {

    /**
     * Provisions a seller-operator account + born-unified identity for a seller
     * (D2: account mint + D5: {@code resolveOrCreate} identity). Idempotent on
     * (tenant, email): re-calling for the same seller converges, never duplicates.
     *
     * @param tenantId    the owning tenant (the account is provisioned under it)
     * @param sellerId    the marketplace seller id (used to derive a deterministic email)
     * @param displayName the seller display name (account displayName)
     * @return the provisioning outcome — {@link ProvisioningResult#successful()} false on
     *         any IAM unavailability (fail-soft); the seller then stays PENDING.
     */
    ProvisioningResult provision(String tenantId, String sellerId, String displayName);

    /**
     * Locks the backing account on seller SUSPEND (D4). Idempotent + fail-soft; a null
     * {@code accountId} is a no-op (net-zero for legacy/PENDING sellers).
     */
    void lockAccount(String tenantId, String accountId);

    /**
     * Deactivates the backing account on seller CLOSE (D4) via the status EP. Idempotent
     * + fail-soft; a null {@code accountId} is a no-op (net-zero).
     */
    void deactivateAccount(String tenantId, String accountId);

    /**
     * Outcome of a provisioning attempt. {@code successful() == true} carries a non-null
     * {@code accountId} (and best-effort {@code identityId}); false means IAM was
     * unavailable and the seller must stay PENDING_PROVISIONING (D3 fail-soft).
     */
    record ProvisioningResult(boolean successful, String accountId, String identityId) {

        public static ProvisioningResult success(String accountId, String identityId) {
            return new ProvisioningResult(true, accountId, identityId);
        }

        public static ProvisioningResult failed() {
            return new ProvisioningResult(false, null, null);
        }
    }
}
