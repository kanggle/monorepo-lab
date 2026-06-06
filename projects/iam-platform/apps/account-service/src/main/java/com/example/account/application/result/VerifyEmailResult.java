package com.example.account.application.result;

import java.time.Instant;

/**
 * Result of a successful verify-email call. Carries the new
 * {@code emailVerifiedAt} so the controller can echo it back in the
 * response without needing to re-query the account.
 */
public record VerifyEmailResult(String accountId, Instant emailVerifiedAt) {}
