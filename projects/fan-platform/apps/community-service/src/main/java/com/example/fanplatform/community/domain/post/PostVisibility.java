package com.example.fanplatform.community.domain.post;

/**
 * Post visibility tier.
 *
 * <p>v1 semantics:
 * <ul>
 *   <li>{@code PUBLIC} — anyone authenticated within the tenant can read.</li>
 *   <li>{@code MEMBERS_ONLY} — only confirmed members of the artist's fandom (or
 *       the post author) can read. v1 reads the membership signal from the
 *       {@code MembershipChecker} port. The frozen GAP demo only had PUBLIC; this
 *       service introduces the gating tier as a first-class concept.</li>
 *   <li>{@code PREMIUM} — paid subscribers only. v1 has no membership-service
 *       (TASK-FAN-BE-002 § Out of Scope) so the gate is effectively always-pass
 *       with a TODO + WARN log; a follow-up task will integrate a real check.</li>
 * </ul>
 */
public enum PostVisibility {
    PUBLIC,
    MEMBERS_ONLY,
    PREMIUM
}
