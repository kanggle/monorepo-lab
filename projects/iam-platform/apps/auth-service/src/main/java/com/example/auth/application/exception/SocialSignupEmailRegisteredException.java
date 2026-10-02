package com.example.auth.application.exception;

/**
 * TASK-BE-620: account-service refused a social signup with {@code 409 ACCOUNT_ALREADY_EXISTS} — the
 * consumer site has no account for this email, but the consumer-account POOL does (an email/password
 * account). Social signup still creates site accounts (pool-aware social is TASK-BE-617), so accepting
 * it would leave a pool account and a site account on one email, which multi-tenancy.md § 소비자 계정 풀
 * § 2 forbids. The browser flow sends the user to the login page to sign in with email and password.
 */
public class SocialSignupEmailRegisteredException extends RuntimeException {

    public SocialSignupEmailRegisteredException(String message) {
        super(message);
    }
}
