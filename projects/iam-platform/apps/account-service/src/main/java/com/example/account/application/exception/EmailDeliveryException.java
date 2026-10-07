package com.example.account.application.exception;

/**
 * TASK-MONO-770 — the {@link com.example.account.application.port.EmailVerificationNotifier} port's failure
 * contract: the mail did not leave, and of which kind the failure is.
 *
 * <p>The adapter (infrastructure) classifies; the use case decides what the person is told
 * (account-api.md § resend-verification-email):
 * <ul>
 *   <li>{@link Kind#TRANSIENT} — connection / authentication / timeout / anything that cannot be judged.
 *       Retrying later may work. 🔴 «Cannot judge» belongs here: calling an unknown failure permanent tells the
 *       person to give up on something that would have worked (the signup.md § 실패의 종류 principle).</li>
 *   <li>{@link Kind#PERMANENT} — the mail server refuses this address (malformed / recipient rejected).
 *       Retrying cannot help.</li>
 * </ul>
 *
 * <p>🔴 R4 ({@code rules/traits/regulated.md}): the message must not carry the recipient address or the token.
 * Adapters build it from a fixed string, never from the SMTP library's message (which can quote the address).
 */
public class EmailDeliveryException extends RuntimeException {

    public enum Kind { TRANSIENT, PERMANENT }

    private final Kind kind;

    public EmailDeliveryException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
