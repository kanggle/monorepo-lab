package com.example.auth.domain.session;

/**
 * Why a device session was revoked. Persisted as VARCHAR(40) and emitted in
 * {@code auth.session.revoked} payloads.
 *
 * <p>Spec: specs/services/auth-service/device-session.md (Data Model — revoke_reason).
 */
public enum RevokeReason {
    /** User invoked DELETE /api/accounts/me/sessions/{deviceId} on this device. */
    USER_REQUESTED,
    /** Concurrent-session limit exceeded; oldest session(s) evicted by D4. */
    EVICTED_BY_LIMIT,
    /** Refresh-token reuse detected; account-wide cascade revoke. */
    TOKEN_REUSE,
    /** Admin-service forced logout. */
    ADMIN_FORCED,
    /** User invoked bulk DELETE /api/accounts/me/sessions ("logout other devices"). */
    LOGOUT_OTHERS
}
