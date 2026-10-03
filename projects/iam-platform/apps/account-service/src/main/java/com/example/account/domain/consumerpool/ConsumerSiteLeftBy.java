package com.example.account.domain.consumerpool;

/**
 * TASK-BE-619 — who made a consumer-site membership {@link ConsumerSiteMembershipStatus#LEFT}
 * ({@code consumer_site_memberships.left_by}, V0032).
 *
 * <p>The two are told apart because they reopen differently (owner decision 2026-10-03 «탈퇴 후 복귀 =
 * 다시 동의하면 복귀»):
 * <ul>
 *   <li>{@link #SELF} — the person left the site themself. Consenting to that site again brings the
 *       membership back to ACTIVE.</li>
 *   <li>{@link #OPERATOR} — that site's operator removed the person from the site. Consent does NOT
 *       reopen it: a kicked-out member cannot let themself back in by pressing «동의».</li>
 * </ul>
 */
public enum ConsumerSiteLeftBy {
    SELF,
    OPERATOR
}
