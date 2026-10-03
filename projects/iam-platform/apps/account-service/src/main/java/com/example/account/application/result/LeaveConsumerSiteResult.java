package com.example.account.application.result;

import java.time.Instant;

/**
 * TASK-BE-619 — the membership after a «사이트 탈퇴» (multi-tenancy.md § 소비자 계정 풀 § 5).
 *
 * @param accountId        the pool account
 * @param siteTenantId     the ONE site it left
 * @param membershipStatus always {@code LEFT}
 * @param leftBy           {@code SELF} | {@code OPERATOR} — who the row now records (an operator removal
 *                         of a self-left member re-records it as {@code OPERATOR})
 * @param leftAt           when the recorded leave happened
 * @param changed          {@code false} when the membership was already LEFT and nothing was written
 * @param accountStatus    the pool account's own status — untouched by leaving
 */
public record LeaveConsumerSiteResult(
        String accountId,
        String siteTenantId,
        String membershipStatus,
        String leftBy,
        Instant leftAt,
        boolean changed,
        String accountStatus) {
}
