package com.example.fanplatform.community.domain.follow;

/**
 * Port for verifying that a follow target is a real artist account
 * (TASK-FAN-BE-045 AC-6, ADR-004 ACCEPTED — A).
 *
 * <h2>Why this port exists</h2>
 *
 * The feed joins {@code posts.author_account_id ⋈ follows.artist_account_id}.
 * Before this check, {@code FollowArtistUseCase} stored whatever string the
 * caller sent — no existence check, no format check — so the field was named
 * {@code artistAccountId} but was tied to nothing, and the join held only by
 * coincidence (the web app happened to send the right value).
 *
 * <h2>Why it is a remote call and not a query</h2>
 *
 * {@code follows} lives in {@code fanplatform_community} and {@code artists} in
 * {@code fanplatform_artist} — <strong>separate databases</strong> — so the
 * reference cannot be a foreign key, and
 * {@code specs/services/community-service/architecture.md} § Forbidden
 * dependencies bars a DB-level reach-in. {@code ADR-004} chose a synchronous
 * internal endpoint over an event projection; the projection would have made this
 * service an inbound event consumer, which its declared composition is not.
 *
 * <p>Implementations MUST be fail-closed: on any infrastructure error return
 * {@code false} (refuse the follow). A validation that opens on error is
 * indistinguishable from having no validation — which is exactly the state this
 * port was created to leave.
 */
public interface ArtistAccountChecker {

    /**
     * @param accountId the claimed artist account (the follow target)
     * @param tenantId  tenant scope
     * @return {@code true} only on a positive confirmation that some artist in
     *         this tenant is authored by {@code accountId}; {@code false} for an
     *         unknown account, a cross-tenant account, and every error
     */
    boolean isArtistAccount(String accountId, String tenantId);
}
