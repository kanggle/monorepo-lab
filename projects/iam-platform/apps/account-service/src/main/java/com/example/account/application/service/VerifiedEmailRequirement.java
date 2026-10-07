package com.example.account.application.service;

import com.example.account.application.exception.EmailNotVerifiedException;
import com.example.account.domain.account.Account;

/**
 * TASK-MONO-770 (ADR-MONO-080 D3 · rider R1) — <b>the one home</b> of «a company role may be attached to this
 * account only if its email is verified».
 *
 * <p>Why a shared predicate and not an inline {@code if}: ADR-MONO-080 gates <i>every</i> write that attaches a
 * company role to a pool account — today the seller-member site-role grant
 * ({@link ConsumerSiteRoleWriteUseCase}), next the operator-invitation acceptance and the partner-participant
 * attach (ADR-MONO-080 D6, {@code TASK-MONO-772}). An inline copy per caller is the shape where a later change
 * lands in one copy and silently not the sibling. 772 calls {@link #require(Account)} as is.
 *
 * <p>What it deliberately does NOT gate (ADR-MONO-080 Alternatives — «로그인에 필수» rejected): login, token
 * issuance, signup, site consent, shopping, the fan site. A person with an unverified email loses nothing they
 * had; only the moment a company role is attached asks for proof.
 *
 * <p>It never revokes: an account that already holds a role keeps it (ADR-MONO-080 D5 — verification is the
 * evidence at the moment of attaching, not a condition for keeping).
 */
public final class VerifiedEmailRequirement {

    private VerifiedEmailRequirement() {
    }

    /**
     * @throws EmailNotVerifiedException if {@code account} has no {@code email_verified_at}
     */
    public static void require(Account account) {
        if (!account.hasVerifiedEmail()) {
            throw new EmailNotVerifiedException(
                    "The account has not verified its email; a company role cannot be attached until it does");
        }
    }
}
