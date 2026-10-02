package com.example.product.application.port;

/**
 * Outbound port for the store {@code SELLER} site role of a seller member's IAM pool account (ADR-MONO-079 D5,
 * TASK-MONO-752; contract {@code specs/contracts/http/internal/product-to-account.md} § 5 · § 6).
 *
 * <p>The two calls have opposite availability stances, on purpose:
 * <ul>
 *   <li>{@link #grant} is the authorization step of a person becoming a member, so it is <b>fail-closed</b> —
 *       it throws, and nothing is linked without IAM's yes.</li>
 *   <li>{@link #revoke} runs on seller SUSPEND / CLOSE, whose domain transition must not block on IAM, so it is
 *       <b>fail-soft</b> — it answers {@code false} and the caller keeps the member in a retryable state.</li>
 * </ul>
 */
public interface SellerSiteRoleGateway {

    /**
     * Writes {@code consumer_site_roles(accountId, siteTenantId, SELLER)}. IAM compares {@code invitedEmail} with
     * the account's own email.
     *
     * @throws com.example.product.domain.exception.SellerInvitationEmailMismatchException  the account is not the
     *         invited email's account
     * @throws com.example.product.domain.exception.SellerMemberAccountNotEligibleException not a pool account /
     *         no ACTIVE store membership / unknown account
     * @throws com.example.product.domain.exception.SellerRoleServiceUnavailableException   anything else
     */
    void grant(String siteTenantId, String accountId, String invitedEmail);

    /**
     * Removes the {@code SELLER} site role. Never locks the account. Idempotent at IAM.
     *
     * @return {@code true} iff IAM confirmed (2xx)
     */
    boolean revoke(String siteTenantId, String accountId);
}
