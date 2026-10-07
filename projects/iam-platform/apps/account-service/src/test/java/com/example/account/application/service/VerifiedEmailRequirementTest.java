package com.example.account.application.service;

import com.example.account.application.exception.EmailNotVerifiedException;
import com.example.account.domain.account.Account;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.TenantId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** TASK-MONO-770 (ADR-MONO-080 D3) — the one predicate TASK-MONO-772 reuses for operator-invitation acceptance. */
@DisplayName("VerifiedEmailRequirement (TASK-MONO-770)")
class VerifiedEmailRequirementTest {

    private static Account account(Instant emailVerifiedAt) {
        return Account.reconstitute("acc-770", TenantId.CONSUMER_POOL, "p@example.com", null, AccountStatus.ACTIVE,
                Instant.EPOCH, Instant.EPOCH, null, null, emailVerifiedAt, 0);
    }

    @Test
    @DisplayName("email_verified_at 없음 → EmailNotVerifiedException · 있으면 통과 · 인증 직후의 같은 계정도 통과")
    void requiresVerifiedEmail() {
        Account unverified = account(null);
        assertThatThrownBy(() -> VerifiedEmailRequirement.require(unverified))
                .isInstanceOf(EmailNotVerifiedException.class);

        unverified.verifyEmail(Instant.parse("2026-10-07T00:00:00Z"));
        assertThatNoException().isThrownBy(() -> VerifiedEmailRequirement.require(unverified));
        assertThatNoException().isThrownBy(() -> VerifiedEmailRequirement.require(account(Instant.EPOCH)));
    }
}
