package com.example.community.domain.access;

/**
 * Port for checking whether an account has access to gated (MEMBERS_ONLY) content.
 * Implementations must be fail-closed: on any downstream error, return {@code false}.
 */
public interface ContentAccessChecker {

    boolean check(String accountId, String requiredPlanLevel);
}
