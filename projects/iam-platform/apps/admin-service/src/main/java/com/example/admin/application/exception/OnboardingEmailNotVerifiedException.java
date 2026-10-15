package com.example.admin.application.exception;

/**
 * TASK-MONO-773 S3 (ADR-MONO-044 D4 · onboarding-api.md «트러스트 게이트») — 403 {@code EMAIL_NOT_VERIFIED}: the
 * self-service caller's personal account has not verified its email (TASK-MONO-770's shared name, passed through
 * from account-service's {@code verified-email:match}). Judged before the tenant is created — nothing is written.
 */
public class OnboardingEmailNotVerifiedException extends RuntimeException {

    public OnboardingEmailNotVerifiedException(String message) {
        super(message);
    }
}
