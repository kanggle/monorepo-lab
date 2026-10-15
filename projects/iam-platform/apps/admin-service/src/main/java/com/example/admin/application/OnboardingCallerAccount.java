package com.example.admin.application;

/**
 * TASK-MONO-773 S3 — the self-service caller's AUTHORITATIVE account record (account-service
 * {@code GET /internal/accounts/{sub}}), as the onboarding entry read it. Its absence ({@code Optional.empty()} at
 * the call sites) is not an error but an input to the trust gate (owner decision option A — an {@code iam}
 * credential has no account record).
 *
 * @param email       the account's email (never from the request body)
 * @param displayName never blank — the controller falls back to the email
 */
public record OnboardingCallerAccount(String email, String displayName) {
}
