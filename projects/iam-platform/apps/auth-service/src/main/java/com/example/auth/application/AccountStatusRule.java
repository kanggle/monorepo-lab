package com.example.auth.application;

import com.example.auth.application.exception.AccountLockedException;
import com.example.auth.application.exception.AccountStatusException;

/**
 * TASK-BE-600 — the ONE rule deciding whether an account's status allows it to sign in.
 *
 * <p>Both interactive login paths apply it: the social callback (via
 * {@link SocialLoginSteps#checkAccountStatus}) and the password form
 * ({@code CredentialAuthenticationProvider}). Before this class the rule lived only in the
 * social path — the form path had no status check at all after TASK-BE-398 removed the
 * JSON login that carried the other copy — so the same LOCKED account was refused through
 * a social provider and admitted with its password. Keeping a single home is what stops the
 * two paths from diverging again.
 *
 * <p>ACTIVE proceeds. LOCKED → {@link AccountLockedException}; DORMANT / DELETED → an
 * {@link AccountStatusException} carrying {@code ACCOUNT_DORMANT} / {@code ACCOUNT_DELETED};
 * any other value (outside account-service's four-value enum) → {@link AccountStatusException}
 * with {@code ACCOUNT_STATUS_UNKNOWN} — an unrecognised status is never read as permission.
 *
 * <p>What each caller SHOWS for a rejection is the caller's decision, not this rule's: the
 * social callback redirects to {@code /login?error=account_unavailable}, while the password
 * form answers exactly like a wrong password (owner decision, TASK-BE-600 AC-1 ⓐ) so that a
 * locked account cannot be used to confirm its password.
 */
public final class AccountStatusRule {

    /** The only status that may sign in. */
    public static final String ACTIVE = "ACTIVE";

    /** {@code auth.login.failed.failureReason} values this rule's rejections map to (auth-events.md enum). */
    public static final String REASON_LOCKED = "ACCOUNT_LOCKED";
    public static final String REASON_DORMANT = "ACCOUNT_DORMANT";
    public static final String REASON_DELETED = "ACCOUNT_DELETED";
    /** Not in the event enum — a rejection with this code emits no {@code failureReason}. */
    public static final String CODE_UNKNOWN = "ACCOUNT_STATUS_UNKNOWN";

    private AccountStatusRule() {
    }

    /**
     * Rejects every non-ACTIVE status.
     *
     * @param status the account status as account-service reports it (never {@code null} —
     *               a missing status is a failed lookup, not a status, and the caller must
     *               treat it as such before reaching this rule)
     * @throws AccountLockedException  when the status is LOCKED
     * @throws AccountStatusException  when the status is DORMANT, DELETED, or unrecognised
     */
    public static void enforce(String status) {
        switch (status) {
            case ACTIVE -> { /* proceed */ }
            case "LOCKED" -> throw new AccountLockedException();
            case "DORMANT" -> throw new AccountStatusException("DORMANT", REASON_DORMANT);
            case "DELETED" -> throw new AccountStatusException("DELETED", REASON_DELETED);
            default -> throw new AccountStatusException(status, CODE_UNKNOWN);
        }
    }
}
