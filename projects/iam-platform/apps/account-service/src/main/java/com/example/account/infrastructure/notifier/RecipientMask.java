package com.example.account.infrastructure.notifier;

/**
 * R4 ({@code rules/traits/regulated.md}) — the one masking rule for a recipient address in this package's logs:
 * the first character of the local part, {@code ***}, then the domain ({@code alice@example.com} →
 * {@code a***@example.com}). {@code "[masked]"} for null, no {@code @}, an empty local part or any failure.
 *
 * <p>Extracted by TASK-MONO-770 from {@link LoggingEmailVerificationNotifier} so the SMTP adapter cannot drift
 * from the stub's format (it mirrors auth-service's sender masking, TASK-BE-111).
 */
final class RecipientMask {

    private RecipientMask() {
    }

    static String mask(String email) {
        try {
            if (email == null) {
                return "[masked]";
            }
            int atIndex = email.indexOf('@');
            if (atIndex < 0) {
                return "[masked]";
            }
            String local = email.substring(0, atIndex);
            String domain = email.substring(atIndex);
            if (local.isEmpty()) {
                return "[masked]";
            }
            return local.charAt(0) + "***" + domain;
        } catch (RuntimeException e) {
            return "[masked]";
        }
    }
}
