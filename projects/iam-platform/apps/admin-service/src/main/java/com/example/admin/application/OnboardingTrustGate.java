package com.example.admin.application;

import com.example.admin.application.exception.OnboardingAccountNotEligibleException;
import com.example.admin.application.exception.OnboardingEmailNotVerifiedException;
import com.example.admin.application.port.AdminOperatorPort;
import com.example.admin.application.port.VerifiedEmailMatchPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * TASK-MONO-773 S3 (ADR-MONO-044 D4 · owner decision 2026-10-15 UTC option A — onboarding-api.md «트러스트 게이트 —
 * 두 자격») — who may self-create a company, judged BEFORE anything is created.
 *
 * <table>
 *   <tr><th>account record</th><th>operator facet</th><th>verdict</th></tr>
 *   <tr><td>present</td><td>any</td><td>personal-account candidate → TASK-MONO-772's
 *       {@link VerifiedEmailMatchPort} (no copy of the predicate): MATCHED → {@link Eligibility#VERIFIED_PERSONAL_ACCOUNT};
 *       NOT_VERIFIED → 403 {@code EMAIL_NOT_VERIFIED}; NOT_ELIGIBLE · EMAIL_MISMATCH → 403
 *       {@code ONBOARDING_ACCOUNT_NOT_ELIGIBLE}; no verdict → the downstream exception propagates (503)</td></tr>
 *   <tr><td>absent (404)</td><td>present</td><td>{@link Eligibility#PROVISIONED_OPERATOR} — an {@code iam}-credential
 *       operator: an administrator-created credential, never a self-signup, so the email check (aimed at anonymous
 *       self-signup) is skipped. The per-identity cap and the gateway rate limit still apply — the caller takes
 *       the existing-facet path only.</td></tr>
 *   <tr><td>absent (404)</td><td>absent</td><td>403 {@code ONBOARDING_ACCOUNT_NOT_ELIGIBLE} — nothing vouches for
 *       this {@code sub} (fail-closed)</td></tr>
 * </table>
 *
 * <p>🔴 «no account record ∧ a facet» is a PROXY for «an {@code iam} credential», not its definition: a personal
 * account whose record was deleted while its facet remains is exempt too (the cap still bounds that row; it can never
 * mint a new row — a 404 without a facet is refused); an {@code iam} credential that does have an account record is
 * judged as a personal-account candidate and refused (the safe side).
 *
 * <p>{@link #accountState} is the same judgement for the read-only «받은 초대» — there the exemption does not carry
 * over (no verified email to key the list on): an absent record is {@code NOT_ELIGIBLE}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OnboardingTrustGate {

    /** Why the caller may create. */
    public enum Eligibility {
        /** An ACTIVE personal account whose email account-service matched as verified. */
        VERIFIED_PERSONAL_ACCOUNT,
        /** No account record, an operator facet exists — the {@code iam}-credential operator (option A). */
        PROVISIONED_OPERATOR
    }

    /** {@code accountState} of «받은 초대» (onboarding-api.md § received-invitations). */
    public enum AccountState { VERIFIED, EMAIL_NOT_VERIFIED, NOT_ELIGIBLE }

    private final VerifiedEmailMatchPort verifiedEmailMatchPort;

    /**
     * @throws OnboardingEmailNotVerifiedException    a personal account whose email is not verified
     * @throws OnboardingAccountNotEligibleException  neither eligibility
     * @throws com.example.admin.application.exception.DownstreamFailureException no verdict (fail-closed)
     */
    public Eligibility require(String accountId, Optional<OnboardingCallerAccount> account,
                               Optional<AdminOperatorPort.OperatorView> facet) {
        if (account.isEmpty()) {
            if (facet.isPresent()) {
                log.info("self-service onboarding: caller has no account record but an operator facet — "
                        + "email check exempt (iam-credential operator, option A) accountId={}", accountId);
                return Eligibility.PROVISIONED_OPERATOR;
            }
            log.info("self-service onboarding refused: no account record and no operator facet accountId={}",
                    accountId);
            throw new OnboardingAccountNotEligibleException();
        }
        VerifiedEmailMatchPort.MatchResult verdict = verifiedEmailMatchPort.match(accountId, account.get().email());
        return switch (verdict.outcome()) {
            case MATCHED -> Eligibility.VERIFIED_PERSONAL_ACCOUNT;
            case NOT_VERIFIED -> throw new OnboardingEmailNotVerifiedException(
                    "Verify your email address before creating a company");
            case NOT_ELIGIBLE, EMAIL_MISMATCH -> {
                log.info("self-service onboarding refused: account not eligible ({}) accountId={}",
                        verdict.outcome(), accountId);
                throw new OnboardingAccountNotEligibleException();
            }
        };
    }

    /** The «받은 초대» verdict — never throws a refusal; no verdict propagates (503, never «no invitations»). */
    public AccountState accountState(String accountId, Optional<OnboardingCallerAccount> account) {
        if (account.isEmpty()) {
            return AccountState.NOT_ELIGIBLE;
        }
        VerifiedEmailMatchPort.MatchResult verdict = verifiedEmailMatchPort.match(accountId, account.get().email());
        return switch (verdict.outcome()) {
            case MATCHED -> AccountState.VERIFIED;
            case NOT_VERIFIED -> AccountState.EMAIL_NOT_VERIFIED;
            case NOT_ELIGIBLE, EMAIL_MISMATCH -> AccountState.NOT_ELIGIBLE;
        };
    }
}
