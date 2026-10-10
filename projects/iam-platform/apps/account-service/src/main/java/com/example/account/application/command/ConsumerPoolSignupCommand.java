package com.example.account.application.command;

/**
 * TASK-MONO-772 S3 (auth-to-account.md § {@code POST /internal/consumer-pool/signups}) — a site-less pool signup.
 * No tenant: the account is always born in {@code consumer-pool}.
 *
 * <p>🔴 {@code toString} hides the password and masks the email (both reach this record from an HTTP body).
 */
public record ConsumerPoolSignupCommand(
        String email,
        String password,
        String displayName,
        String locale,
        String timezone
) {
    @Override
    public String toString() {
        return "ConsumerPoolSignupCommand[email=<masked>, password=<redacted>, displayName=" + displayName
                + ", locale=" + locale + ", timezone=" + timezone + "]";
    }
}
