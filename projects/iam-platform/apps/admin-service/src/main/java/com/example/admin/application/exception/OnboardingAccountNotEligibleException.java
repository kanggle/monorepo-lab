package com.example.admin.application.exception;

/**
 * TASK-MONO-773 S3 (owner decision option A · onboarding-api.md «트러스트 게이트 — 두 자격») — 403
 * {@code ONBOARDING_ACCOUNT_NOT_ELIGIBLE}: the caller holds neither eligibility — not an ACTIVE personal account
 * whose verified email account-service can match, and not an existing operator whose identity has no account record
 * (an {@code iam}-credential operator). Judged before the tenant is created — nothing is written.
 *
 * <p>The message carries the guidance the contract requires («개인 계정으로 로그인해 만드세요»); the console picks
 * its own copy by the code.
 */
public class OnboardingAccountNotEligibleException extends RuntimeException {

    public static final String GUIDANCE =
            "이 계정으로는 회사를 만들 수 없습니다. 사이트나 회사용 계정이라면 개인 계정으로 로그인해 만드세요";

    public OnboardingAccountNotEligibleException() {
        super(GUIDANCE);
    }
}
