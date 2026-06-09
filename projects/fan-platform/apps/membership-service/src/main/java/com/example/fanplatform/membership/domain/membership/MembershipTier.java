package com.example.fanplatform.membership.domain.membership;

/**
 * Membership tier. {@code PREMIUM} is the strict superset of {@code MEMBERS_ONLY}
 * (see {@code com.example.fanplatform.membership.domain.access.AccessPolicy#tierGrants}).
 */
public enum MembershipTier {
    MEMBERS_ONLY,
    PREMIUM
}
