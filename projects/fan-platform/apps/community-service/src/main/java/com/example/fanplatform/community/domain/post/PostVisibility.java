package com.example.fanplatform.community.domain.post;

/**
 * Post visibility tier.
 *
 * <p>Semantics — all three are <b>enforced</b>; {@code PostAccessGuard} is the single
 * enforcement point and is the authority on this:
 * <ul>
 *   <li>{@code PUBLIC} — anyone authenticated within the tenant can read.</li>
 *   <li>{@code MEMBERS_ONLY} — only confirmed members of the artist's fandom (or
 *       the post author) can read. The membership signal comes from the
 *       {@code MembershipChecker} port. The frozen GAP demo only had PUBLIC; this
 *       service introduces the gating tier as a first-class concept.</li>
 *   <li>{@code PREMIUM} — the post author, or a member whose tier
 *       {@code MembershipChecker} verifies as {@code PREMIUM}. Tier hierarchy is
 *       resolved in membership-service. <b>Hard fail-close</b> (TASK-FAN-BE-010): any
 *       downstream or auth error resolves to "not a member", so the post stays locked.</li>
 * </ul>
 *
 * <p><b>Do not read PREMIUM as an open gate.</b> This javadoc described the v1 state
 * (TASK-FAN-BE-002 shipped without membership-service, so PREMIUM was an always-pass
 * with a TODO + WARN log) and was never updated when TASK-FAN-BE-010 wired the real
 * check. The stale sentence outlived the gap by long enough to be re-reported as a live
 * security hole by more than one backlog sweep — each of which paid to re-derive that
 * {@code PostAccessGuard} had in fact been closing it all along (TASK-MONO-354).
 */
public enum PostVisibility {
    PUBLIC,
    MEMBERS_ONLY,
    PREMIUM
}
